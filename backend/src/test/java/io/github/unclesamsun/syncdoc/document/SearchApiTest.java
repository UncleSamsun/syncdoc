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

/**
 * API-019 검색. 결과는 현재 프로젝트의 한 게시본 안에서만 나와야 한다 —
 * 검색은 권한 경계가 가장 쉽게 새는 곳이다.
 */
@Import(SearchApiTest.Gateways.class)
class SearchApiTest extends PostgresContainerSupport {

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

    private ResponseEntity<String> search(Actor actor, UUID projectId, String query) {
        return rest.exchange("/api/v1/projects/" + projectId + "/search?q=" + query, HttpMethod.GET,
                new HttpEntity<>(null, actor.headers()), String.class);
    }

    @Test
    void a_hit_carries_the_path_the_anchor_and_an_excerpt() {
        Actor owner = actor("9101", "gho_a");
        ProjectEntity project = projectVisibleTo(owner, "gho_a", "1001");
        contents().putFile("rev-1", "docs/spec.md", """
                # 명세

                ## 수집 규칙

                게시본은 revision 하나에 고정한다. 임대 token이 소유를 증명한다.
                """);
        collect(project.getId());

        ResponseEntity<String> response = search(owner, project.getId(), "임대");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("docs/spec.md")
                .contains("\"anchor\":\"수집-규칙\"")
                .contains("임대 token");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void another_projects_documents_never_show_up() {
        Actor owner = actor("9102", "gho_b");
        ProjectEntity mine = projectVisibleTo(owner, "gho_b", "1002");
        ProjectEntity other = projectVisibleTo(owner, "gho_b", "1003");
        contents().putFile("rev-1", "docs/shared-word.md", "# 다른 프로젝트\n\n여기에만 있는 낱말 오소리");
        collect(other.getId());
        contents().reset();
        contents().putFile("rev-1", "docs/mine.md", "# 내 문서\n\n다른 내용");
        collect(mine.getId());

        assertThat(search(owner, mine.getId(), "오소리").getBody())
                .contains("\"total\":0")
                .doesNotContain("shared-word");
    }

    @Test
    void a_user_who_cannot_see_the_project_cannot_search_it() {
        Actor owner = actor("9103", "gho_c");
        Actor stranger = actor("9104", "gho_d");
        ProjectEntity project = projectVisibleTo(owner, "gho_c", "1004");
        contents().putFile("rev-1", "docs/spec.md", "# 명세\n\n비밀스러운 낱말");
        collect(project.getId());

        assertThat(search(stranger, project.getId(), "비밀스러운").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void an_empty_or_overlong_query_is_refused_instead_of_being_trimmed() {
        Actor owner = actor("9105", "gho_e");
        ProjectEntity project = projectVisibleTo(owner, "gho_e", "1005");
        contents().putFile("rev-1", "docs/spec.md", "# 명세");
        collect(project.getId());

        assertThat(search(owner, project.getId(), "").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        ResponseEntity<String> tooLong = search(owner, project.getId(), "가".repeat(201));
        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(tooLong.getBody()).contains("200자까지입니다");
    }

    @Test
    void before_the_first_collection_search_has_no_snapshot_to_look_in() {
        Actor owner = actor("9106", "gho_f");
        ProjectEntity project = projectVisibleTo(owner, "gho_f", "1006");

        ResponseEntity<String> response = search(owner, project.getId(), "무엇이든");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"snapshotId\":null").contains("\"total\":0");
    }

    @Test
    void a_word_that_appears_only_in_a_title_is_still_found() {
        Actor owner = actor("9107", "gho_g");
        ProjectEntity project = projectVisibleTo(owner, "gho_g", "1007");
        contents().putFile("rev-1", "docs/spec.md", "# 오소리 안내서\n\n본문에는 다른 말만 있다");
        collect(project.getId());

        assertThat(search(owner, project.getId(), "오소리").getBody()).contains("\"total\":1");
    }
}
