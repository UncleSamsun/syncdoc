package io.github.unclesamsun.syncdoc.spec;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.auth.CsrfTokenFilter;
import io.github.unclesamsun.syncdoc.auth.SessionService;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.crypto.TokenCipher;
import io.github.unclesamsun.syncdoc.github.FakeRepositoryAccessGateway;
import io.github.unclesamsun.syncdoc.github.FakeRepositoryContentGateway;
import io.github.unclesamsun.syncdoc.github.GitHubRepository;
import io.github.unclesamsun.syncdoc.github.RepositoryAccessGateway;
import io.github.unclesamsun.syncdoc.github.RepositoryContentGateway;
import io.github.unclesamsun.syncdoc.project.domain.ProjectEntity;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import io.github.unclesamsun.syncdoc.support.ProjectFixture;
import io.github.unclesamsun.syncdoc.sync.SyncQueue;
import io.github.unclesamsun.syncdoc.sync.SyncWorker;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(SpecTraceabilityApiTest.Gateways.class)
class SpecTraceabilityApiTest extends PostgresContainerSupport {
    @TestConfiguration
    static class Gateways {

        @Bean
        @Primary
        RepositoryAccessGateway fakeRepositories() {
            return new FakeRepositoryAccessGateway();
        }

        @Bean
        @Primary
        RepositoryContentGateway fakeContents() {
            return new FakeRepositoryContentGateway();
        }
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    UserRepository users;
    @Autowired
    UserCredentialRepository credentials;
    @Autowired
    SessionService sessions;
    @Autowired
    TokenCipher cipher;
    @Autowired
    ProjectFixture fixture;
    @Autowired
    SyncQueue queue;
    @Autowired
    SyncWorker worker;
    @Autowired
    ObjectMapper json;
    @Autowired
    RepositoryAccessGateway accessGateway;
    @Autowired
    RepositoryContentGateway contentGateway;

    @BeforeEach
    void resetGateways() {
        ((FakeRepositoryAccessGateway) accessGateway).reset();
        ((FakeRepositoryContentGateway) contentGateway).reset();
    }


    @Autowired io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotRepository snapshots;
    @Autowired io.github.unclesamsun.syncdoc.project.domain.ProjectRepository projects;
    static String md(String id, String type, String body) { return "---\nid: " + id + "\ntype: " + type + "\nstatus: 확정\n---\n\n" + body; }
    String path(ProjectEntity p) { return "/api/v1/projects/" + p.getId() + "/spec-traceability"; }
    void collect(ProjectEntity p) { queue.request(p.getId(), true); assertThat(worker.runOnce()).isTrue(); }
    @Test void relations_pagination_diagnostics_and_permissions_use_pinned_snapshot() {
        var owner = signIn("9001"); var p = connect("9001", "gho_a", "901");
        var fake = (FakeRepositoryContentGateway) contentGateway;
        fake.putFile("rev-1", "docs/req.md", md("DOC-001", "prd-requirements", "## REQ-001 로그인\n\n## REQ-002 읽기"));
        fake.putFile("rev-1", "docs/task.md", md("DOC-002", "tasks", "## TASK-001 구현\n\n**근거:** REQ-001, REQ-099"));
        fake.putIssue("issue1", 7, "TASK-001: 구현", "open", null, java.util.List.of());
        collect(p);
        var response = get(owner, path(p) + "?size=1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store", "private");
        var view = json.readTree(response.getBody());
        assertThat(view.path("totalElements").asInt()).isEqualTo(2);
        assertThat(view.path("requirements").get(0).path("coverage").asString()).isEqualTo("linked");
        assertThat(view.path("requirements").get(0).path("tasks").get(0).path("execution").path("issueNumber").asInt()).isEqualTo(7);
        String snapshot = view.path("snapshotId").asString();
        assertThat(json.readTree(body(owner,path(p)+"?snapshotId="+snapshot+"&coverage=unlinked")).path("requirements").get(0).path("item").path("itemId").asString()).isEqualTo("REQ-002");
        assertThat(json.readTree(body(owner,path(p)+"/findings?snapshotId="+snapshot)).path("findings").get(0).path("code").asString()).isEqualTo("MISSING_REQUIREMENT");
        for (String query : java.util.List.of("?page=-1", "?size=0", "?size=101", "?coverage=invalid")) assertThat(get(owner,path(p)+query).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var stranger = signIn("9002");
        assertThat(get(stranger,path(p)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(new HttpHeaders(),path(p)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(owner,path(p)+"?snapshotId="+UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.GONE);
        var other = connect("9001", "unused", "902");
        assertThat(get(owner,path(other)+"?snapshotId="+snapshot).getStatusCode()).isEqualTo(HttpStatus.GONE);
        String stored = snapshots.findById(UUID.fromString(snapshot)).orElseThrow().getTraceabilityJson();
        fake.reset(); fake.putIssue("issue1",7,"TASK-001: 구현","closed","completed",java.util.List.of()); collect(p);
        var refreshed = json.readTree(body(owner,path(p)+"?snapshotId="+snapshot));
        assertThat(refreshed.path("requirements").get(0).path("tasks").get(0).path("execution").path("status").asString()).isEqualTo("done");
        assertThat(snapshots.findById(UUID.fromString(snapshot)).orElseThrow().getTraceabilityJson()).isEqualTo(stored);

    }
    @Test void first_collection_and_legacy_null_are_distinct() {
        var owner = signIn("9003"); var p = connect("9003", "gho_a", "903");
        assertThat(get(owner,path(p)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ((FakeRepositoryContentGateway)contentGateway).putFile("rev-1", "docs/readme.md", "# 안내");
        collect(p);
        var snapshot = snapshots.findById(projects.findById(p.getId()).orElseThrow().getCurrentSnapshotId()).orElseThrow();
        snapshot.traceability(null); snapshots.saveAndFlush(snapshot);
        var report = json.readTree(body(owner,path(p)));
        assertThat(report.path("analysisStatus").asString()).isEqualTo("unchecked");
        assertThat(report.path("uncheckedReason").asString()).isEqualTo("NOT_COMPUTED");
        assertThat(report.path("analysisVersion").isNull()).isTrue();
    }
    private HttpHeaders signIn(String githubUserId) {
        Instant now = Instant.now();
        UserEntity user = users.save(
                new UserEntity(UUID.randomUUID(), githubUserId, "u" + githubUserId, now, now));
        credentials.save(new UserCredentialEntity(user.getId(), cipher.encrypt("gho_" + githubUserId),
                null, null, null, 1));
        String raw = sessions.issue(user.getId()).rawToken();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, SessionService.COOKIE_NAME + "=" + raw);
        headers.add(CsrfTokenFilter.HEADER, sessions.csrfTokenFor(raw));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ProjectEntity connect(String githubUserId, String token, String repositoryId) {
        UUID userId = users.findByGithubUserId(githubUserId).orElseThrow().getId();
        ProjectEntity project = fixture.newProject(repositoryId, userId);
        ((FakeRepositoryAccessGateway) accessGateway).registerRepository("gho_" + githubUserId,
                new GitHubRepository(repositoryId, project.getFullName(), false, "main", "11"));
        return project;
    }

    private ResponseEntity<String> get(HttpHeaders headers, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers), String.class);
    }

    private String body(HttpHeaders headers, String path) {
        ResponseEntity<String> response = get(headers, path);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
