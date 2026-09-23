package io.github.unclesamsun.syncdoc.project;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.auth.CsrfTokenFilter;
import io.github.unclesamsun.syncdoc.auth.SessionService;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.crypto.TokenCipher;
import io.github.unclesamsun.syncdoc.github.FakeRepositoryAccessGateway;
import io.github.unclesamsun.syncdoc.github.GitHubRepository;
import io.github.unclesamsun.syncdoc.github.RepositoryAccessGateway;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.List;
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

@Import(ProjectConnectionTest.Gateways.class)
class ProjectConnectionTest extends PostgresContainerSupport {

    @TestConfiguration
    static class Gateways {

        @Bean
        @Primary
        RepositoryAccessGateway fakeRepositories() {
            return new FakeRepositoryAccessGateway();
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
    ProjectRepository projects;
    @Autowired
    RepositoryAccessGateway gateway;

    private FakeRepositoryAccessGateway fake() {
        return (FakeRepositoryAccessGateway) gateway;
    }

    @BeforeEach
    void resetGateway() {
        fake().reset();
    }

    private record Actor(HttpHeaders headers) {
    }

    /** 세션과 GitHub 토큰을 함께 갖춘 사용자를 만든다. 토큰이 없으면 GitHub를 부를 수 없다. */
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
        return new Actor(headers);
    }

    private void repositoryVisibleTo(String githubAccessToken, String repositoryId, String fullName) {
        fake().registerRepository(githubAccessToken,
                new GitHubRepository(repositoryId, fullName, false, "main", "11"));
        fake().registerBranch(repositoryId, "main", true);
        fake().registerPath(repositoryId, "main", "docs");
    }

    private ResponseEntity<String> connect(Actor actor, String body) {
        return rest.exchange("/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>(body, actor.headers()), String.class);
    }

    @Test
    void connecting_a_visible_repository_creates_one_project_waiting_for_its_first_sync() {
        Actor owner = actor("1001", "gho_owner");
        repositoryVisibleTo("gho_owner", "101", "UncleSamsun/syncdoc");

        ResponseEntity<String> response = connect(owner,
                "{\"githubRepositoryId\":\"101\",\"branch\":\"main\",\"docsRoot\":\"docs\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"syncState\":\"queued\"").contains("\"branch\":\"main\"");
        assertThat(projects.findAll()).hasSize(1);
    }

    @Test
    void connecting_the_same_repository_again_returns_409_with_the_existing_project() {
        Actor owner = actor("1002", "gho_owner2");
        repositoryVisibleTo("gho_owner2", "102", "o/r2");
        connect(owner, "{\"githubRepositoryId\":\"102\"}");

        ResponseEntity<String> again = connect(owner, "{\"githubRepositoryId\":\"102\"}");

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody()).contains("\"projectId\":\"")
                .contains(projects.findByGithubRepositoryId("102").orElseThrow().getId().toString());
        assertThat(projects.findAll()).hasSize(1);
    }

    @Test
    void two_concurrent_requests_still_produce_a_single_project() throws Exception {
        Actor owner = actor("1003", "gho_owner3");
        repositoryVisibleTo("gho_owner3", "103", "o/r3");

        Callable<ResponseEntity<String>> call =
                () -> connect(owner, "{\"githubRepositoryId\":\"103\"}");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ResponseEntity<String>>> results = pool.invokeAll(List.of(call, call));
            List<HttpStatus> codes = List.of(
                    (HttpStatus) results.get(0).get().getStatusCode(),
                    (HttpStatus) results.get(1).get().getStatusCode());
            assertThat(codes).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        } finally {
            pool.shutdownNow();
        }
        assertThat(projects.findAll()).hasSize(1);
    }

    @Test
    void a_repository_the_user_cannot_see_is_not_connectable() {
        Actor stranger = actor("1004", "gho_stranger");
        repositoryVisibleTo("gho_someone_else", "104", "o/r4");

        ResponseEntity<String> response = connect(stranger, "{\"githubRepositoryId\":\"104\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(projects.findAll()).isEmpty();
    }

    @Test
    void a_branch_that_does_not_exist_is_rejected_with_the_field_named() {
        Actor owner = actor("1005", "gho_owner5");
        repositoryVisibleTo("gho_owner5", "105", "o/r5");

        ResponseEntity<String> response = connect(owner,
                "{\"githubRepositoryId\":\"105\",\"branch\":\"nope\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).contains("\"field\":\"branch\"");
        assertThat(projects.findAll()).isEmpty();
    }

    @Test
    void a_docs_path_that_does_not_exist_is_rejected_with_the_screen_wording() {
        Actor owner = actor("1006", "gho_owner6");
        repositoryVisibleTo("gho_owner6", "106", "o/r6");

        ResponseEntity<String> response = connect(owner,
                "{\"githubRepositoryId\":\"106\",\"branch\":\"main\",\"docsRoot\":\"spec\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).contains("\"field\":\"docsRoot\"")
                .contains("이 브랜치에 spec 경로가 없습니다.");
    }

    @Test
    void a_path_that_could_escape_the_repository_is_rejected_before_github_is_called() {
        Actor owner = actor("1007", "gho_owner7");
        // 저장소를 등록하지 않았는데도 422가 나오면 GitHub를 부르기 전에 걸렀다는 뜻이다.
        ResponseEntity<String> response = connect(owner,
                "{\"githubRepositoryId\":\"107\",\"docsRoot\":\"../etc\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void connecting_without_the_csrf_header_is_rejected() {
        Actor owner = actor("1008", "gho_owner8");
        repositoryVisibleTo("gho_owner8", "108", "o/r8");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, owner.headers().getFirst(HttpHeaders.COOKIE));
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = rest.exchange("/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>("{\"githubRepositoryId\":\"108\"}", headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(projects.findAll()).isEmpty();
    }

    @Test
    void an_omitted_branch_and_path_fall_back_to_the_defaults() {
        Actor owner = actor("1009", "gho_owner9");
        repositoryVisibleTo("gho_owner9", "109", "o/r9");

        ResponseEntity<String> response = connect(owner, "{\"githubRepositoryId\":\"109\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"branch\":\"main\"").contains("\"docsRoot\":\"docs\"");
    }
}
