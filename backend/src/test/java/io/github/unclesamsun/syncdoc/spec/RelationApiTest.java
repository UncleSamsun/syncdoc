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

@Import(RelationApiTest.Gateways.class)
class RelationApiTest extends PostgresContainerSupport {
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
    @Test void relation_routes_pin_snapshot_and_join_designs_and_current_execution() {
        var owner=signIn("9201");var p=connect("9201","unused","921");var fake=(FakeRepositoryContentGateway)contentGateway;
        fake.putFile("rev-1","docs/req.md",md("DOC-001","prd-requirements","## REQ-001 읽기\nA"));
        fake.putFile("rev-1","docs/task.md",md("DOC-002","tasks","## TASK-001 구현\n\n**근거:** REQ-001, UI-001, API-001"));
        fake.putFile("rev-1","docs/ui.md",md("DOC-003","ui-screens","## UI-001 화면\n\n**연결 요구:** REQ-001"));
        fake.putFile("rev-1","docs/api.md",md("DOC-004","tech-interface","## 계약 일람\n\n| ID | 연결 요구 |\n|---|---|\n| API-001 | REQ-001 |"));
        UUID first=collect(p);
        String path=base(p)+"/spec-relations?snapshotId="+first;
        var response=get(owner,path);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("private","no-store");
        var view=json.readTree(response.getBody());assertThat(view.path("analysisStatus").asString()).isEqualTo("complete");
        assertThat(view.path("requirements").get(0).path("designs")).hasSize(2);
        assertThat(view.path("requirements").get(0).path("tasks").get(0).path("execution").path("status").asString()).isEqualTo("unregistered");
        assertThat(view.path("snapshotId").asString()).isEqualTo(first.toString());
        assertThat(get(signIn("9202"),path).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(new HttpHeaders(),path).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(owner,path+"&size=0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(owner,base(p)+"/spec-relations?snapshotId="+UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.GONE);
        fake.head("rev-2");
        fake.putFile("rev-2","docs/req.md",md("DOC-001","prd-requirements","## REQ-001 읽기\nB"));
        for(String name:java.util.List.of("task","ui","api")) fake.putFile("rev-2","docs/"+name+".md",fake.readTextAt(new io.github.unclesamsun.syncdoc.github.RepositoryContentGateway.RepositoryRef("1","921","owner/repo"),"rev-1","docs/"+name+".md",100000).orElseThrow());
        UUID second=collect(p);
        var impacts=json.readTree(body(owner,base(p)+"/snapshot-comparison/design-impacts"+query(first,second)));
        assertThat(impacts.path("items")).hasSize(2);
        assertThat(impacts.path("items").get(0).path("before").path("documentId").asString()).isNotEqualTo(impacts.path("items").get(0).path("after").path("documentId").asString());
        assertThat(json.readTree(body(owner,base(p)+"/snapshot-comparison/design-impacts"+query(second,second))).path("items")).isEmpty();
        var old=snapshots.findById(first).orElseThrow();String report=old.getRelationsJson();old.relations(null);snapshots.saveAndFlush(old);
        assertThat(json.readTree(body(owner,path)).path("analysisStatus").asString()).isEqualTo("unchecked");
        assertThat(json.readTree(body(owner,base(p)+"/snapshot-comparison/design-impacts"+query(first,second))).path("status").asString()).isEqualTo("unchecked");
        old.relations("{\"schemaVersion\":99}");snapshots.saveAndFlush(old);
        assertThat(json.readTree(body(owner,path)).path("analysisStatus").asString()).isEqualTo("unchecked");
        old.relations(report);snapshots.saveAndFlush(old);
        p=projects.findById(p.getId()).orElseThrow();p.reconfigure("main","other",null);projects.saveAndFlush(p);fake.docsRoot("other");fake.putFile("rev-2","other/a.md","# 다른 범위");
        UUID third=collect(p);
        assertThat(get(owner,base(p)+"/snapshot-comparison/design-impacts"+query(first,third)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
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
