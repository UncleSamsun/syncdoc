package io.github.unclesamsun.syncdoc.sync;

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
import io.github.unclesamsun.syncdoc.project.domain.ProjectEntity;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import io.github.unclesamsun.syncdoc.support.ProjectFixture;
import io.github.unclesamsun.syncdoc.sync.domain.SyncJobRepository;
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
 * API-013·API-014를 HTTP로 확인한다. 권한 경계와 응답에 무엇이 담기지 않는지가 검사 대상이다.
 */
@Import(SyncApiTest.Gateways.class)
class SyncApiTest extends PostgresContainerSupport {

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
    ProjectFixture fixture;
    @Autowired
    SyncJobRepository jobs;
    @Autowired
    RepositoryAccessGateway gateway;

    private FakeRepositoryAccessGateway fake() {
        return (FakeRepositoryAccessGateway) gateway;
    }

    @BeforeEach
    void resetGateway() {
        fake().reset();
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

    /** 그 사용자가 GitHub에서 이 저장소를 볼 수 있게 만든다. */
    private void visibleTo(String githubAccessToken, ProjectEntity project) {
        fake().registerRepository(githubAccessToken, new GitHubRepository(
                project.getGithubRepositoryId(), project.getFullName(), false, "main", "11"));
    }

    private ProjectEntity projectConnectedBy(Actor owner, String repositoryId) {
        return fixture.newProject(repositoryId, owner.userId());
    }

    private ResponseEntity<String> requestSync(Actor actor, UUID projectId) {
        return rest.exchange("/api/v1/projects/" + projectId + "/sync", HttpMethod.POST,
                new HttpEntity<>(null, actor.headers()), String.class);
    }

    private ResponseEntity<String> readStatus(Actor actor, UUID projectId) {
        return rest.exchange("/api/v1/projects/" + projectId + "/sync", HttpMethod.GET,
                new HttpEntity<>(null, actor.headers()), String.class);
    }

    @Test
    void an_unauthenticated_request_is_refused() {
        ProjectEntity project = fixture.newProject("601");

        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/projects/" + project.getId() + "/sync", null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void the_connector_can_ask_for_a_collection() {
        Actor owner = actor("6001", "gho_owner");
        ProjectEntity project = projectConnectedBy(owner, "602");
        visibleTo("gho_owner", project);

        ResponseEntity<String> response = requestSync(owner, project.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).contains("\"jobId\"").contains("\"reused\":false");
    }

    @Test
    void asking_twice_joins_the_same_job() {
        Actor owner = actor("6002", "gho_owner2");
        ProjectEntity project = projectConnectedBy(owner, "603");
        visibleTo("gho_owner2", project);

        requestSync(owner, project.getId());
        ResponseEntity<String> again = requestSync(owner, project.getId());

        assertThat(again.getBody()).contains("\"reused\":true");
        assertThat(jobs.findByProjectIdOrderByCreatedAtDesc(project.getId())).hasSize(1);
    }

    @Test
    void someone_who_only_reads_the_project_cannot_ask_for_a_collection() {
        Actor owner = actor("6003", "gho_owner3");
        Actor reader = actor("6004", "gho_reader");
        ProjectEntity project = projectConnectedBy(owner, "604");
        visibleTo("gho_owner3", project);
        visibleTo("gho_reader", project);

        ResponseEntity<String> response = requestSync(reader, project.getId());

        // 권한이 없다고 알리지 않는다. 없는 대상과 같은 응답이다.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("RESOURCE_NOT_FOUND");
        assertThat(jobs.findByProjectIdOrderByCreatedAtDesc(project.getId())).isEmpty();
    }

    @Test
    void a_user_who_cannot_see_the_repository_gets_the_same_answer_as_for_a_missing_one() {
        Actor owner = actor("6005", "gho_owner5");
        Actor stranger = actor("6006", "gho_stranger");
        ProjectEntity project = projectConnectedBy(owner, "605");
        visibleTo("gho_owner5", project);

        assertThat(readStatus(stranger, project.getId()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(readStatus(stranger, UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void the_status_tells_what_is_waiting_without_leaking_internals() {
        Actor owner = actor("6007", "gho_owner7");
        ProjectEntity project = projectConnectedBy(owner, "606");
        visibleTo("gho_owner7", project);
        requestSync(owner, project.getId());

        ResponseEntity<String> status = readStatus(owner, project.getId());

        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status.getBody())
                .contains("\"state\":\"queued\"")
                .contains("\"pending\":true")
                .doesNotContain("gho_")
                .doesNotContain("lease")
                .doesNotContain("C:\\\\");
    }
}
