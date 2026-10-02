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

@Import(SnapshotComparisonApiTest.Gateways.class)
class SnapshotComparisonApiTest extends PostgresContainerSupport {
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



    @Autowired io.github.unclesamsun.syncdoc.project.domain.ProjectRepository projects;
    @Autowired io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotRepository snapshots;
    static String md(String id,String type,String body){return "---\nid: "+id+"\ntype: "+type+"\nstatus: 확정\n---\n\n"+body;}
    UUID collect(ProjectEntity p){queue.request(p.getId(),true);worker.runOnce();return projects.findById(p.getId()).orElseThrow().getCurrentSnapshotId();}
    String base(ProjectEntity p){return "/api/v1/projects/"+p.getId();}
    String query(UUID a,UUID b){return "?fromSnapshotId="+a+"&toSnapshotId="+b;}
    @Test void body_changes_paginated_routes_and_permission_boundaries() {
        var owner=signIn("9101");var project=connect("9101","unused","991");var fake=(FakeRepositoryContentGateway)contentGateway;
        fake.putFile("rev-1","docs/req.md",md("DOC-001","prd-requirements","## REQ-001 동일 제목\nA"));
        fake.putFile("rev-1","docs/task.md",md("DOC-002","tasks","## TASK-001 구현\n\n**근거:** REQ-001"));
        UUID a=collect(project);fake.head("rev-2");
        fake.putFile("rev-2","docs/req.md",md("DOC-001","prd-requirements","## REQ-001 동일 제목\nB"));
        fake.putFile("rev-2","docs/task.md",md("DOC-002","tasks","## TASK-001 구현\n\n**근거:** REQ-001"));
        UUID b=collect(project);String path=base(project)+"/snapshot-comparison";String q=query(a,b);
        var response=get(owner,path+q);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("private","no-store");
        var summary=json.readTree(response.getBody());assertThat(summary.path("status").asString()).isEqualTo("complete");
        assertThat(summary.path("counts").path("items").path("modified").asInt()).isEqualTo(1);
        assertThat(json.readTree(body(owner,path+"/items"+q+"&kind=req&change=modified&size=1")).path("items").get(0).path("after").path("title").asString()).isEqualTo("동일 제목");
        assertThat(json.readTree(body(owner,path+"/impacts"+q)).path("items").get(0).path("taskId").asString()).isEqualTo("TASK-001");
        assertThat(json.readTree(body(owner,base(project)+"/snapshots?size=1")).path("totalElements").asInt()).isEqualTo(2);
        for(String bad:java.util.List.of("&page=-1","&size=101","&kind=bad","&change=bad"))assertThat(get(owner,path+"/items"+q+bad).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(json.readTree(body(owner,path+"/items"+q+"&page=2147483647&size=100")).path("items")).isEmpty();
        assertThat(get(signIn("9102"),path+q).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(new HttpHeaders(),path+q).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(owner,path+query(UUID.randomUUID(),b)).getStatusCode()).isEqualTo(HttpStatus.GONE);
        var other=connect("9101","unused","992");assertThat(get(owner,base(other)+"/snapshot-comparison"+q).getStatusCode()).isEqualTo(HttpStatus.GONE);
        var same=json.readTree(body(owner,path+query(b,b)));assertThat(same.path("counts").path("items").path("modified").asInt()).isZero();
        assertThat(get(owner,path).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var stranger=signIn("9104");assertThat(get(stranger,base(project)+"/snapshots").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var old=snapshots.findById(a).orElseThrow();String original=old.getComparisonJson();old.comparison("{\"schemaVersion\":99}");snapshots.saveAndFlush(old);
        assertThat(json.readTree(body(owner,path+q)).path("status").asString()).isEqualTo("unchecked");
        old.comparison(original);old.markComplete();snapshots.saveAndFlush(old);
        old.comparison(null);snapshots.saveAndFlush(old);
        var legacy=json.readTree(body(owner,path+q));assertThat(legacy.path("status").asString()).isEqualTo("unchecked");assertThat(legacy.path("counts").isNull()).isTrue();
    }
    @Test void same_commit_with_different_scope_returns_conflict() {
        var owner=signIn("9103");var p=connect("9103","unused","993");var fake=(FakeRepositoryContentGateway)contentGateway;
        fake.putFile("rev-1","docs/a.md","# A");fake.putFile("rev-1","other/a.md","# B");UUID a=collect(p);
        p=projects.findById(p.getId()).orElseThrow();p.reconfigure("main","other",null);projects.saveAndFlush(p);fake.docsRoot("other");UUID b=collect(p);
        assertThat(get(owner,base(p)+"/snapshot-comparison"+query(a,b)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
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
