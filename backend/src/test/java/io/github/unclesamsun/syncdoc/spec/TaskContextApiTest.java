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

@Import(TaskContextApiTest.Gateways.class)
class TaskContextApiTest extends PostgresContainerSupport {
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
    @Test void context_is_pinned_and_raw_commands_and_rules_are_exported_without_execution() {
        var owner=signIn("9301");var p=connect("9301","unused","931");var fake=(FakeRepositoryContentGateway)contentGateway;
        fake.putFile("rev-1","docs/req.md",md("DOC-001","prd-requirements","## REQ-001 요구\n본문"));
        fake.putFile("rev-1","docs/task.md",md("DOC-002","tasks","## TASK-001 구현\n\n**목적:** 목적\n\n**근거:** REQ-001\n\n**범위:** 범위\n\n**선행:** 없음\n\n**산출물:** 파일\n\n**검증:** `gradlew test`\n\n**완료:** 사람 확인"));
        for(String rule:TaskContextIndexer.RULE_PATHS)fake.putFile("rev-1",rule,"rule-content");
        UUID first=collect(p);String path=base(p)+"/tasks/TASK-001/context?snapshotId="+first;
        var response=get(owner,path);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).contains("private","no-store");
        var view=json.readTree(response.getBody());assertThat(view.path("state").asString()).isEqualTo("complete");
        assertThat(view.path("task").path("fields").path("검증").asString()).contains("`gradlew test`");
        assertThat(view.path("rules")).hasSize(10);assertThat(view.path("rules").get(0).path("sourceHash").asString()).hasSize(64);
        assertThat(view.path("markdown").asString()).contains(first.toString(),"rev-1","참고 자료", "blob/rev-1/");
        assertThat(view.path("markdown").asString()).contains("[req REQ-001]", "?plain=1#L7");
        var markdown=get(owner,path+"&format=markdown");assertThat(markdown.getHeaders().getContentType().toString()).contains("text/markdown");assertThat(markdown.getBody()).contains("gradlew test");
        assertThat(get(new HttpHeaders(),path).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(signIn("9302"),path).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(owner,path+"&format=exe").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(owner,base(p)+"/tasks/TASK-000/context").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get(owner,base(p)+"/tasks/TASK-999/context").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(owner,base(p)+"/tasks/TASK-001/context?snapshotId="+UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.GONE);
        var old=snapshots.findById(first).orElseThrow();String context=old.getContextJson();
        fake.putIssue("i1",8,"TASK-001: 구현","closed","completed",java.util.List.of());fake.putPullRequest(81,"TASK-001: 증거","closed",true);fake.putPullRequest(82,"TASK-001: 추가","open",false);collect(p);
        assertThat(json.readTree(body(owner,path)).path("execution").path("status").asString()).isEqualTo("done");
        assertThat(json.readTree(body(owner,path)).path("execution").path("pullRequests")).hasSize(2);
        assertThat(body(owner,path+"&format=markdown")).contains("/pull/81", "/pull/82");
        assertThat(snapshots.findById(first).orElseThrow().getContextJson()).isEqualTo(context);
        old.context(null);snapshots.saveAndFlush(old);assertThat(json.readTree(body(owner,path)).path("state").asString()).isEqualTo("unchecked");
        old.context("{\"schemaVersion\":99}");snapshots.saveAndFlush(old);assertThat(json.readTree(body(owner,path)).path("state").asString()).isEqualTo("unchecked");
        old.context(context);snapshots.saveAndFlush(old);
        var duplicated=json.readTree(context);((tools.jackson.databind.node.ArrayNode)duplicated.path("tasks")).add(duplicated.path("tasks").get(0));old.context(json.writeValueAsString(duplicated));snapshots.saveAndFlush(old);
        assertThat(get(owner,path).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test void a_missing_rule_does_not_make_known_missing_task_return_success() {
        var owner=signIn("9303");var p=connect("9303","unused","933");var fake=(FakeRepositoryContentGateway)contentGateway;
        fake.putFile("rev-1","docs/task.md",md("DOC-001","tasks","## TASK-001 알려진 작업\n\n**근거:** 없음"));collect(p);
        assertThat(get(owner,base(p)+"/tasks/TASK-999/context").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
