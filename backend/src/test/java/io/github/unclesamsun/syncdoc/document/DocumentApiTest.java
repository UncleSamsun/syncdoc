package io.github.unclesamsun.syncdoc.document;

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
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * API-017·API-018을 HTTP로 확인한다. 권한 경계, 첫 수집 전 상태, 회수된 게시본, 표시할 수 없는 문서가
 * 각각 다른 응답으로 구분되는지가 검사 대상이다.
 */
@Import(DocumentApiTest.Gateways.class)
class DocumentApiTest extends PostgresContainerSupport {

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
    ProjectRepository projects;
    @Autowired
    SyncQueue queue;
    @Autowired
    SyncWorker worker;
    @Autowired
    JdbcTemplate jdbc;
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

    private UUID currentSnapshot(UUID projectId) {
        return projects.findById(projectId).orElseThrow().getCurrentSnapshotId();
    }

    @Test
    void before_the_first_collection_the_list_is_empty_and_the_body_is_not_ready() {
        Actor owner = actor("7001", "gho_a");
        ProjectEntity project = projectVisibleTo(owner, "gho_a", "701");

        ResponseEntity<String> list = get(owner, "/api/v1/projects/" + project.getId() + "/documents");
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("\"snapshotId\":null").contains("\"items\":[]");

        ResponseEntity<String> body = get(owner,
                "/api/v1/projects/" + project.getId() + "/documents/" + UUID.randomUUID());
        assertThat(body.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body.getBody()).contains("DOCUMENTS_NOT_READY");
    }

    @Test
    void a_collected_document_can_be_listed_and_read() {
        Actor owner = actor("7002", "gho_b");
        ProjectEntity project = projectVisibleTo(owner, "gho_b", "702");
        contents().putFile("rev-1", "docs/guide.md", """
                ---
                id: DOC-1
                type: guide
                ---

                # 안내서

                | 항목 | 값 |
                |---|---|
                | 상태 | 확정 |

                ```mermaid
                flowchart TD
                  A --> B
                ```
                """);
        collect(project.getId());

        ResponseEntity<String> list = get(owner, "/api/v1/projects/" + project.getId() + "/documents");
        assertThat(list.getBody()).contains("docs/guide.md").contains("안내서").contains("\"kind\":\"guide\"");

        String documentId = list.getBody().replaceAll(".*\"items\":\\[\\{\"id\":\"([^\"]+)\".*", "$1");
        ResponseEntity<String> body = get(owner,
                "/api/v1/projects/" + project.getId() + "/documents/" + documentId);

        assertThat(body.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body.getBody())
                .contains("<table>")
                .contains("data-diagram-id")
                .contains("flowchart TD")
                .contains("\"specId\":\"DOC-1\"");
        // 본문은 저장하지 않는 응답이다.
        assertThat(body.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void a_user_who_cannot_see_the_project_gets_the_same_answer_as_for_a_missing_one() {
        Actor owner = actor("7003", "gho_c");
        Actor stranger = actor("7004", "gho_d");
        ProjectEntity project = projectVisibleTo(owner, "gho_c", "703");
        contents().putFile("rev-1", "docs/guide.md", "# 안내서");
        collect(project.getId());

        assertThat(get(stranger, "/api/v1/projects/" + project.getId() + "/documents").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(stranger, "/api/v1/projects/" + UUID.randomUUID() + "/documents").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void a_document_from_another_project_is_not_reachable_through_this_one() {
        Actor owner = actor("7005", "gho_e");
        ProjectEntity mine = projectVisibleTo(owner, "gho_e", "704");
        ProjectEntity other = projectVisibleTo(owner, "gho_e", "705");
        contents().putFile("rev-1", "docs/guide.md", "# 안내서");
        collect(mine.getId());
        collect(other.getId());

        UUID otherDocument = UUID.fromString(jdbc.queryForObject(
                "select id::text from documents where snapshot_id = ?", String.class,
                currentSnapshot(other.getId())));

        assertThat(get(owner, "/api/v1/projects/" + mine.getId() + "/documents/" + otherDocument)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void a_snapshot_that_no_longer_exists_is_gone_not_missing() {
        Actor owner = actor("7006", "gho_f");
        ProjectEntity project = projectVisibleTo(owner, "gho_f", "706");
        contents().putFile("rev-1", "docs/guide.md", "# 안내서");
        collect(project.getId());

        ResponseEntity<String> response = get(owner, "/api/v1/projects/" + project.getId()
                + "/documents?snapshotId=" + UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody()).contains("SNAPSHOT_GONE");
    }

    @Test
    void a_document_without_a_conversion_says_it_cannot_be_shown() {
        Actor owner = actor("7007", "gho_g");
        ProjectEntity project = projectVisibleTo(owner, "gho_g", "707");
        contents().putFile("rev-1", "docs/guide.md", "# 안내서");
        collect(project.getId());
        // 변환기가 붙기 전에 수집한 문서와 같은 상태로 되돌린다.
        jdbc.update("update documents set state = 'collected', html = null where snapshot_id = ?",
                currentSnapshot(project.getId()));
        UUID documentId = UUID.fromString(jdbc.queryForObject(
                "select id::text from documents where snapshot_id = ?", String.class,
                currentSnapshot(project.getId())));

        ResponseEntity<String> response = get(owner,
                "/api/v1/projects/" + project.getId() + "/documents/" + documentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("DOCUMENT_NOT_RENDERABLE");
    }

    @Test
    void a_long_list_is_read_one_page_at_a_time() {
        Actor owner = actor("7008", "gho_h");
        ProjectEntity project = projectVisibleTo(owner, "gho_h", "708");
        contents().putFile("rev-1", "docs/a.md", "# 하나");
        contents().putFile("rev-1", "docs/b.md", "# 둘");
        contents().putFile("rev-1", "docs/c.md", "# 셋");
        collect(project.getId());

        ResponseEntity<String> first = get(owner,
                "/api/v1/projects/" + project.getId() + "/documents?limit=2");
        assertThat(first.getBody()).contains("docs/a.md").contains("docs/b.md").doesNotContain("docs/c.md");

        String cursor = first.getBody().replaceAll(".*\"nextCursor\":\"([^\"]+)\".*", "$1");
        ResponseEntity<String> second = get(owner,
                "/api/v1/projects/" + project.getId() + "/documents?limit=2&cursor=" + cursor);

        assertThat(second.getBody()).contains("docs/c.md").doesNotContain("docs/a.md");
        assertThat(second.getBody()).contains("\"nextCursor\":null");
    }

    @Test
    void an_unauthenticated_request_is_refused() {
        ProjectEntity project = fixture.newProject("709");

        assertThat(rest.getForEntity("/api/v1/projects/" + project.getId() + "/documents", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
