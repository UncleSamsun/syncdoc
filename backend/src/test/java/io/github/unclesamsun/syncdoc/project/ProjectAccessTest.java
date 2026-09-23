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
import io.github.unclesamsun.syncdoc.github.GitHubLookupFailedException;
import io.github.unclesamsun.syncdoc.github.GitHubRepository;
import io.github.unclesamsun.syncdoc.github.RepositoryAccessGateway;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
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
import org.springframework.test.context.TestPropertySource;

/** 목록·상세·수정의 권한 경계. 관리자 권한이 GitHub 열람 권한을 대신하지 않는다. */
@Import(ProjectAccessTest.Gateways.class)
@TestPropertySource(properties = "syncdoc.auth.admin-github-user-id=1")
class ProjectAccessTest extends PostgresContainerSupport {

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

    private void visible(String githubAccessToken, String repositoryId, String fullName) {
        fake().registerRepository(githubAccessToken,
                new GitHubRepository(repositoryId, fullName, false, "main", "11"));
    }

    private void branchAndPath(String repositoryId, String branch, String path) {
        fake().registerBranch(repositoryId, branch, branch.equals("main"));
        fake().registerPath(repositoryId, branch, path);
    }

    private UUID connect(Actor actor, String repositoryId) {
        ResponseEntity<String> response = rest.exchange("/api/v1/projects", HttpMethod.POST,
                new HttpEntity<>("{\"githubRepositoryId\":\"" + repositoryId + "\"}", actor.headers()),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return projects.findByGithubRepositoryId(repositoryId).orElseThrow().getId();
    }

    private ResponseEntity<String> get(String path, Actor actor) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(actor.headers()), String.class);
    }

    private ResponseEntity<String> patch(String path, Actor actor, String body) {
        return rest.exchange(path, HttpMethod.PATCH, new HttpEntity<>(body, actor.headers()), String.class);
    }

    @Test
    void the_list_shows_projects_the_requester_can_see_even_if_someone_else_connected_them() {
        Actor owner = actor("2001", "gho_owner");
        Actor teammate = actor("2002", "gho_teammate");
        visible("gho_owner", "201", "o/shared");
        visible("gho_teammate", "201", "o/shared");
        branchAndPath("201", "main", "docs");
        connect(owner, "201");

        assertThat(get("/api/v1/projects", teammate).getBody()).contains("o/shared");
    }

    @Test
    void a_project_whose_repository_the_requester_cannot_see_is_hidden_from_the_list() {
        Actor owner = actor("2003", "gho_owner3");
        Actor stranger = actor("2004", "gho_stranger");
        visible("gho_owner3", "203", "o/private");
        branchAndPath("203", "main", "docs");
        connect(owner, "203");

        assertThat(get("/api/v1/projects", stranger).getBody()).doesNotContain("o/private");
    }

    @Test
    void that_project_answers_404_not_403_so_its_existence_stays_hidden() {
        Actor owner = actor("2005", "gho_owner5");
        Actor stranger = actor("2006", "gho_stranger6");
        visible("gho_owner5", "205", "o/private5");
        branchAndPath("205", "main", "docs");
        UUID projectId = connect(owner, "205");

        assertThat(get("/api/v1/projects/" + projectId, stranger).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /** 확인 불가는 허용도 거절도 아닌 별개 상태다. 예전 데이터를 돌려주지 않는다. */
    @Test
    void when_github_cannot_be_reached_the_list_is_503_not_a_stale_answer() {
        Actor owner = actor("2007", "gho_owner7");
        visible("gho_owner7", "207", "o/r7");
        branchAndPath("207", "main", "docs");
        connect(owner, "207");

        fake().failWith(new GitHubLookupFailedException("GitHub가 503으로 답했다"));
        ResponseEntity<String> response = get("/api/v1/projects", owner);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).doesNotContain("o/r7");
    }

    @Test
    void the_connector_can_change_the_branch_and_the_version_moves() {
        Actor owner = actor("2008", "gho_owner8");
        visible("gho_owner8", "208", "o/r8");
        branchAndPath("208", "main", "docs");
        branchAndPath("208", "dev", "docs");
        UUID projectId = connect(owner, "208");
        long version = projects.findById(projectId).orElseThrow().getVersion();

        ResponseEntity<String> response = patch("/api/v1/projects/" + projectId, owner,
                "{\"branch\":\"dev\",\"expectedVersion\":" + version + "}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"branch\":\"dev\"");
        assertThat(projects.findById(projectId).orElseThrow().getVersion()).isGreaterThan(version);
    }

    @Test
    void a_stale_expected_version_is_a_conflict() {
        Actor owner = actor("2009", "gho_owner9");
        visible("gho_owner9", "209", "o/r9");
        branchAndPath("209", "main", "docs");
        branchAndPath("209", "dev", "docs");
        UUID projectId = connect(owner, "209");
        long version = projects.findById(projectId).orElseThrow().getVersion();
        patch("/api/v1/projects/" + projectId, owner,
                "{\"branch\":\"dev\",\"expectedVersion\":" + version + "}");

        ResponseEntity<String> second = patch("/api/v1/projects/" + projectId, owner,
                "{\"branch\":\"main\",\"expectedVersion\":" + version + "}");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).contains("\"code\":\"VERSION_CONFLICT\"");
    }

    @Test
    void someone_who_only_reads_the_project_cannot_change_it_and_gets_404() {
        Actor owner = actor("2010", "gho_owner10");
        Actor teammate = actor("2011", "gho_teammate11");
        visible("gho_owner10", "210", "o/r10");
        visible("gho_teammate11", "210", "o/r10");
        branchAndPath("210", "main", "docs");
        branchAndPath("210", "dev", "docs");
        UUID projectId = connect(owner, "210");
        long version = projects.findById(projectId).orElseThrow().getVersion();

        // 읽을 수는 있다.
        assertThat(get("/api/v1/projects/" + projectId, teammate).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = patch("/api/v1/projects/" + projectId, teammate,
                "{\"branch\":\"dev\",\"expectedVersion\":" + version + "}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(projects.findById(projectId).orElseThrow().getBranch()).isEqualTo("main");
    }

    @Test
    void the_service_admin_can_change_a_project_someone_else_connected() {
        Actor owner = actor("2012", "gho_owner12");
        Actor admin = actor("1", "gho_admin");
        visible("gho_owner12", "212", "o/r12");
        visible("gho_admin", "212", "o/r12");
        branchAndPath("212", "main", "docs");
        branchAndPath("212", "dev", "docs");
        UUID projectId = connect(owner, "212");
        long version = projects.findById(projectId).orElseThrow().getVersion();

        ResponseEntity<String> response = patch("/api/v1/projects/" + projectId, admin,
                "{\"branch\":\"dev\",\"expectedVersion\":" + version + "}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** 관리자라도 GitHub에서 볼 수 없으면 접근할 수 없다. */
    @Test
    void the_service_admin_still_needs_github_access() {
        Actor owner = actor("2013", "gho_owner13");
        Actor admin = actor("1", "gho_admin13");
        visible("gho_owner13", "213", "o/r13");
        branchAndPath("213", "main", "docs");
        UUID projectId = connect(owner, "213");

        assertThat(get("/api/v1/projects/" + projectId, admin).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void the_list_tells_the_caller_whether_it_can_manage_each_project() {
        Actor owner = actor("2014", "gho_owner14");
        Actor teammate = actor("2015", "gho_teammate15");
        visible("gho_owner14", "214", "o/r14");
        visible("gho_teammate15", "214", "o/r14");
        branchAndPath("214", "main", "docs");
        connect(owner, "214");

        assertThat(get("/api/v1/projects", owner).getBody()).contains("\"manageable\":true");
        assertThat(get("/api/v1/projects", teammate).getBody()).contains("\"manageable\":false");
    }
}
