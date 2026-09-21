package io.github.unclesamsun.syncdoc.dashboard;

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
import io.github.unclesamsun.syncdoc.github.GitHubLookupFailedException;
import io.github.unclesamsun.syncdoc.github.GitHubRepository;
import io.github.unclesamsun.syncdoc.github.RepositoryAccessGateway;
import io.github.unclesamsun.syncdoc.github.RepositoryContentGateway;
import io.github.unclesamsun.syncdoc.project.domain.ProjectEntity;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import io.github.unclesamsun.syncdoc.support.ProjectFixture;
import io.github.unclesamsun.syncdoc.sync.SyncQueue;
import io.github.unclesamsun.syncdoc.sync.SyncWorker;
import java.time.Instant;
import java.util.List;
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

/**
 * API-015·API-016의 집계 규칙과 권한 경계.
 *
 * <p>집계에서 틀리면 조용히 틀린다 — 숫자는 언제나 그럴듯해 보인다. 그래서 취소·미등록·충돌·분모 0처럼
 * 값이 왜곡되기 쉬운 경우를 하나씩 확인한다.
 */
@Import(VisibilityAndProgressTest.Gateways.class)
class VisibilityAndProgressTest extends PostgresContainerSupport {

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

    /** 작업계획 문서 하나. 제목 단계와 frontmatter 종류가 작업 추출의 조건이다. */
    private static final String PLAN = """
            ---
            id: DOC-014
            type: tasks
            ---

            # MVP 구현 계획

            ## TASK-001 실행 기반

            첫 작업이다.

            ## TASK-002 초대와 세션

            두 번째 작업이다.

            ## TASK-003 저장소 연결

            세 번째 작업이다.
            """;

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
    ProjectRepository projects;
    @Autowired
    SyncQueue queue;
    @Autowired
    SyncWorker worker;
    @Autowired
    RepositoryAccessGateway accessGateway;
    @Autowired
    RepositoryContentGateway contentGateway;

    private FakeRepositoryAccessGateway access() {
        return (FakeRepositoryAccessGateway) accessGateway;
    }

    private FakeRepositoryContentGateway contents() {
        return (FakeRepositoryContentGateway) contentGateway;
    }

    @BeforeEach
    void resetGateways() {
        access().reset();
        contents().reset();
        contents().putFile("rev-1", "docs/04-tasks/implementation-plan.md", PLAN);
    }

    private record Actor(UUID userId, HttpHeaders headers) {
    }

    private Actor actor(String githubUserId, String githubAccessToken) {
        Instant now = Instant.now();
        UserEntity user = users.save(
                new UserEntity(UUID.randomUUID(), githubUserId, "u" + githubUserId, now, now));
        credentials.save(new UserCredentialEntity(user.getId(), cipher.encrypt(githubAccessToken),
                null, null, null, 1));
        String raw = sessions.issue(user.getId()).rawToken();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, SessionService.COOKIE_NAME + "=" + raw);
        headers.add(CsrfTokenFilter.HEADER, sessions.csrfTokenFor(raw));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new Actor(user.getId(), headers);
    }

    private ProjectEntity projectVisibleTo(Actor owner, String token, String repositoryId) {
        ProjectEntity project = fixture.newProject(repositoryId, owner.userId());
        access().registerRepository(token, new GitHubRepository(
                repositoryId, project.getFullName(), false, "main", "11"));
        return project;
    }

    private void collect(UUID projectId) {
        queue.request(projectId, true);
        assertThat(worker.runOnce()).isTrue();
    }

    private ResponseEntity<String> get(Actor actor, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(null, actor.headers()), String.class);
    }

    private String overview(Actor actor, UUID projectId) {
        ResponseEntity<String> response = get(actor, "/api/v1/projects/" + projectId + "/overview");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @Test
    void tasks_in_the_plan_show_up_even_when_no_issue_claims_them() {
        Actor owner = actor("9001", "gho_a");
        ProjectEntity project = projectVisibleTo(owner, "gho_a", "901");
        collect(project.getId());

        String body = overview(owner, project.getId());

        assertThat(body).contains("TASK-001").contains("TASK-002").contains("TASK-003");
        // Issue가 없다고 작업이 목록에서 빠지면 완료율이 실제보다 높아진다.
        assertThat(body).contains("\"unregistered\":3");
        assertThat(body).contains("\"denominator\":3");
    }

    @Test
    void a_canceled_task_leaves_both_the_numerator_and_the_denominator() {
        Actor owner = actor("9002", "gho_b");
        ProjectEntity project = projectVisibleTo(owner, "gho_b", "902");
        contents().putIssue("I_1", 11, "TASK-001: 실행 기반", "closed", "completed", List.of("UncleSamsun"));
        contents().putIssue("I_2", 12, "TASK-002: 초대와 세션", "closed", "not_planned", List.of());
        contents().putIssue("I_3", 13, "TASK-003: 저장소 연결", "open", null, List.of());
        collect(project.getId());

        String body = overview(owner, project.getId());

        assertThat(body).contains("\"done\":1").contains("\"canceled\":1").contains("\"inProgress\":1");
        // 분모는 취소를 뺀 2이고 완료는 1이다. 취소를 완료로 세지도, 분모에 남기지도 않는다.
        assertThat(body).contains("\"completed\":1").contains("\"denominator\":2").contains("\"ratio\":0.5");
    }

    @Test
    void two_issues_claiming_one_task_do_not_pick_a_winner() {
        Actor owner = actor("9003", "gho_c");
        ProjectEntity project = projectVisibleTo(owner, "gho_c", "903");
        contents().putIssue("I_1", 11, "TASK-001: 실행 기반", "closed", "completed", List.of());
        contents().putIssue("I_2", 12, "TASK-001: 실행 기반 다시", "open", null, List.of());
        collect(project.getId());

        String body = overview(owner, project.getId());

        assertThat(body).contains("mapping_conflict");
        // 충돌을 완료로 세지 않는다. 어느 쪽이 맞는지는 사람이 정한다.
        assertThat(body).contains("\"done\":0");
    }

    @Test
    void several_pull_requests_on_one_task_are_all_listed_and_counted_once() {
        Actor owner = actor("9004", "gho_d");
        ProjectEntity project = projectVisibleTo(owner, "gho_d", "904");
        contents().putIssue("I_1", 11, "TASK-001: 실행 기반", "closed", "completed", List.of());
        contents().putPullRequest(21, "feat: TASK-001 실행 기반 1부", "closed", true);
        contents().putPullRequest(22, "feat: TASK-001 실행 기반 2부", "closed", true);
        collect(project.getId());

        String body = overview(owner, project.getId());

        assertThat(body).contains("21").contains("22");
        // PR이 둘이어도 완료는 하나다. PR 수를 완료 수로 세지 않는다.
        assertThat(body).contains("\"done\":1");
    }

    @Test
    void an_issue_for_a_task_that_is_not_in_the_plan_is_reported_separately() {
        Actor owner = actor("9005", "gho_e");
        ProjectEntity project = projectVisibleTo(owner, "gho_e", "905");
        contents().putIssue("I_9", 19, "TASK-099: 명세에 없는 작업", "open", null, List.of());
        collect(project.getId());

        String body = overview(owner, project.getId());

        assertThat(body).contains("TASK-099");
        // 명세에 없으므로 분모에 넣지 않는다. 그렇다고 숨기지도 않는다.
        assertThat(body).contains("\"total\":3");
    }

    @Test
    void a_project_with_no_confirmed_task_says_there_is_nothing_to_count() {
        Actor owner = actor("9006", "gho_f");
        ProjectEntity project = projectVisibleTo(owner, "gho_f", "906");
        contents().reset();
        contents().putFile("rev-1", "docs/guide.md", "---\ntype: guide\n---\n\n# 안내서");
        collect(project.getId());

        String body = overview(owner, project.getId());

        // 분모 0은 0%가 아니라 계산 대상 없음이다.
        assertThat(body).contains("\"denominator\":0").contains("\"ratio\":null");
    }

    @Test
    void a_project_without_a_github_project_is_not_reported_as_a_permission_problem() {
        Actor owner = actor("9007", "gho_g");
        ProjectEntity project = projectVisibleTo(owner, "gho_g", "907");
        collect(project.getId());

        assertThat(overview(owner, project.getId())).contains("\"projectAccess\":\"not_connected\"");
    }

    @Test
    void issues_we_could_not_read_leave_the_aggregate_marked_incomplete() {
        Actor owner = actor("9008", "gho_h");
        ProjectEntity project = projectVisibleTo(owner, "gho_h", "908");
        contents().failIssuesWith(new GitHubLookupFailedException("Issue 권한이 없다"));
        collect(project.getId());

        String body = overview(owner, project.getId());

        // 읽지 못한 사실을 숨기면 불완전한 값이 확정된 값처럼 보인다.
        assertThat(body).contains("\"issues\":true");
        // 그래도 문서 현황은 계속 보인다.
        assertThat(body).contains("\"documentCount\":1");
    }

    @Test
    void hitting_the_issue_page_limit_also_marks_the_aggregate_incomplete() {
        Actor owner = actor("9009", "gho_i");
        ProjectEntity project = projectVisibleTo(owner, "gho_i", "909");
        contents().putIssue("I_1", 11, "TASK-001: 실행 기반", "open", null, List.of());
        contents().issuesIncomplete();
        collect(project.getId());

        assertThat(overview(owner, project.getId())).contains("\"issues\":true");
    }

    @Test
    void someone_who_cannot_see_the_project_cannot_see_its_numbers() {
        Actor owner = actor("9010", "gho_j");
        Actor stranger = actor("9011", "gho_k");
        ProjectEntity project = projectVisibleTo(owner, "gho_j", "910");
        collect(project.getId());

        assertThat(get(stranger, "/api/v1/projects/" + project.getId() + "/overview").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(stranger, "/api/v1/projects/" + project.getId() + "/tasks").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void the_task_list_carries_the_same_numbers_as_the_overview() {
        Actor owner = actor("9012", "gho_l");
        ProjectEntity project = projectVisibleTo(owner, "gho_l", "911");
        contents().putIssue("I_1", 11, "TASK-001: 실행 기반", "closed", "completed", List.of("someone"));
        collect(project.getId());

        ResponseEntity<String> tasks = get(owner, "/api/v1/projects/" + project.getId() + "/tasks");
        assertThat(tasks.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tasks.getBody()).contains("\"total\":3").contains("TASK-001").contains("someone");

        ResponseEntity<String> done = get(owner,
                "/api/v1/projects/" + project.getId() + "/tasks?status=done");
        assertThat(done.getBody()).contains("TASK-001").doesNotContain("TASK-002");
    }

    @Test
    void before_the_first_collection_the_overview_has_no_snapshot_and_no_made_up_numbers() {
        Actor owner = actor("9013", "gho_m");
        ProjectEntity project = projectVisibleTo(owner, "gho_m", "912");

        String body = overview(owner, project.getId());

        assertThat(body).contains("\"snapshotId\":null").contains("\"documents\":true");
        assertThat(body).contains("\"ratio\":null");
    }
}
