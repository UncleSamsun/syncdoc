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

/**
 * API-025를 HTTP로 확인한다.
 *
 * <p>판정 자체는 {@link ChecklistCheckerTest}가 검사기 fixture로 확인한다. 여기서 보는 것은 다른
 * 것이다. 판정이 수집할 때 게시본에 붙는지, 규칙 파일이 없는 저장소가 통과로 보이지 않는지,
 * 첫 수집 전과 권한 경계가 다른 계약과 같게 답하는지다.
 */
@Import(SpecChecklistApiTest.Gateways.class)
class SpecChecklistApiTest extends PostgresContainerSupport {

    private static final String FORMAT = """
            {"types": [
              {"type": "guide", "name": "안내서", "spec": false, "condition": "언제나",
               "required": [], "labels": [], "checks": []},
              {"type": "prd-requirements", "name": "요구", "spec": true, "condition": "언제나",
               "required": ["근거"], "labels": ["근거"],
               "checks": [{"unit": "section", "idPrefix": "REQ", "labels": ["근거"]}]}
            ]}
            """;
    private static final String SETTINGS = """
            # 프로젝트 설정

            ## 적용 Spec

            | type | 적용 | 사유 |
            |---|---|---|
            | guide | 적용 | |
            | prd-requirements | 적용 | |
            | tech-ops | 보류 | 배포 설계 전이다 |
            """;

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

    @Test
    void before_the_first_collection_it_is_a_conflict_and_not_an_empty_result() {
        HttpHeaders owner = signIn("8001");
        ProjectEntity project = connect("8001", "gho_a", "801");

        ResponseEntity<String> response = get(owner, path(project));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("DOCUMENTS_NOT_READY");
    }

    @Test
    void a_repository_that_follows_the_rules_reports_pass_and_the_deferred_type_as_pending() {
        HttpHeaders owner = signIn("8002");
        ProjectEntity project = connect("8002", "gho_b", "802");
        FakeRepositoryContentGateway contents = (FakeRepositoryContentGateway) contentGateway;
        contents.putFile("rev-1", "rules/spec-format.json", FORMAT);
        contents.putFile("rev-1", "rules/project-settings.md", SETTINGS);
        contents.putFile("rev-1", "docs/guide.md", """
                ---
                id: DOC-001
                type: guide
                ---

                # 안내서
                """);
        contents.putFile("rev-1", "docs/req.md", """
                ---
                id: DOC-002
                type: prd-requirements
                ---

                # 요구

                ## REQ-001 첫 요구

                **근거:** 사용자가 정했다.
                """);
        collect(project);

        JsonNode view = json.readTree(body(owner, path(project)));

        assertThat(view.path("status").asString()).isEqualTo("pending");
        assertThat(view.path("findings")).isEmpty();
        assertThat(view.path("sourceRevision").asString()).isEqualTo("rev-1");
        assertThat(typeOf(view, "guide").path("status").asString()).isEqualTo("pass");
        assertThat(typeOf(view, "guide").path("documents").get(0).path("path").asString())
                .isEqualTo("docs/guide.md");
        // `보류`로 둔 종류는 문서가 없어도 오류가 아니다. 미작성과 오류를 같게 두지 않는다.
        assertThat(typeOf(view, "tech-ops").path("status").asString()).isEqualTo("pending");
        assertThat(typeOf(view, "tech-ops").path("reason").asString()).isNotEmpty();
    }

    @Test
    void a_missing_label_is_reported_under_the_type_with_the_place_to_fix() {
        HttpHeaders owner = signIn("8003");
        ProjectEntity project = connect("8003", "gho_c", "803");
        FakeRepositoryContentGateway contents = (FakeRepositoryContentGateway) contentGateway;
        contents.putFile("rev-1", "rules/spec-format.json", FORMAT);
        contents.putFile("rev-1", "rules/project-settings.md", SETTINGS);
        contents.putFile("rev-1", "docs/guide.md", """
                ---
                id: DOC-001
                type: guide
                ---

                # 안내서
                """);
        contents.putFile("rev-1", "docs/req.md", """
                ---
                id: DOC-002
                type: prd-requirements
                ---

                # 요구

                ## REQ-001 첫 요구

                근거를 굵은 라벨로 쓰지 않았다.
                """);
        collect(project);

        JsonNode view = json.readTree(body(owner, path(project)));

        assertThat(view.path("status").asString()).isEqualTo("error");
        JsonNode finding = typeOf(view, "prd-requirements").path("findings").get(0);
        assertThat(finding.path("check").asString()).isEqualTo("C2");
        assertThat(finding.path("path").asString()).isEqualTo("docs/req.md");
        // 어디를 고쳐야 하는지 줄까지 준다. 문서 전체를 다시 읽게 하지 않는다.
        assertThat(finding.path("line").asInt()).isGreaterThan(1);
        assertThat(finding.path("message").asString()).contains("근거");
        assertThat(finding.path("documentId").asString()).isNotEmpty();
    }

    @Test
    void a_repository_without_rule_files_is_unchecked_and_never_pass() {
        HttpHeaders owner = signIn("8004");
        ProjectEntity project = connect("8004", "gho_d", "804");
        ((FakeRepositoryContentGateway) contentGateway).putFile("rev-1", "docs/guide.md", """
                ---
                id: DOC-001
                type: guide
                ---

                # 안내서
                """);
        collect(project);

        JsonNode view = json.readTree(body(owner, path(project)));

        assertThat(view.path("status").asString()).isEqualTo("unchecked");
        assertThat(view.path("uncheckedReason").asString()).isEqualTo("DEFINITION_MISSING");
        assertThat(view.path("types")).isEmpty();
    }

    @Test
    void someone_who_cannot_see_the_project_gets_the_same_answer_as_for_a_missing_one() {
        HttpHeaders owner = signIn("8005");
        ProjectEntity project = connect("8005", "gho_e", "805");
        HttpHeaders stranger = signIn("8006");

        ResponseEntity<String> response = get(stranger, path(project));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("RESOURCE_NOT_FOUND").doesNotContain(project.getFullName());
    }

    private static String path(ProjectEntity project) {
        return "/api/v1/projects/" + project.getId() + "/spec-checklist";
    }

    private void collect(ProjectEntity project) {
        queue.request(project.getId(), true);
        assertThat(worker.runOnce()).isTrue();
    }

    private static JsonNode typeOf(JsonNode view, String type) {
        for (JsonNode candidate : view.path("types")) {
            if (type.equals(candidate.path("type").asString())) {
                return candidate;
            }
        }
        throw new AssertionError("종류 " + type + "가 응답에 없다");
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
