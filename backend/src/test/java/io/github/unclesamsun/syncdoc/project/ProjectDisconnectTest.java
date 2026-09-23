package io.github.unclesamsun.syncdoc.project;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.auth.CsrfTokenFilter;
import io.github.unclesamsun.syncdoc.auth.SessionService;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.crypto.TokenCipher;
import io.github.unclesamsun.syncdoc.dashboard.domain.TaskRepository;
import io.github.unclesamsun.syncdoc.document.domain.AssetContentRepository;
import io.github.unclesamsun.syncdoc.document.domain.AssetRepository;
import io.github.unclesamsun.syncdoc.document.domain.DocumentRepository;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotRepository;
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
import io.github.unclesamsun.syncdoc.sync.domain.SyncJobRepository;
import io.github.unclesamsun.syncdoc.sync.domain.SyncRunRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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
 * API-026 연결 해제.
 *
 * <p>끊었는데 문서 본문이 서버에 남아 있으면 끊었다는 말과 실제가 다르다. 그래서 무엇이
 * 지워졌는지를 표마다 확인한다. 같은 바이트를 가리키는 다른 프로젝트의 그림이 함께 사라지지
 * 않는지도 본다 — 첨부 내용은 내용 해시가 열쇠라 여러 게시본이 같은 행을 가리킨다.
 */
@Import(ProjectDisconnectTest.Gateways.class)
class ProjectDisconnectTest extends PostgresContainerSupport {

    /** 1×1 투명 PNG. 첨부가 실제로 담기는 경로를 지나가게 하려고 쓴다. */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

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
    DocumentSnapshotRepository snapshots;
    @Autowired
    DocumentRepository documents;
    @Autowired
    AssetRepository assets;
    @Autowired
    AssetContentRepository assetContents;
    @Autowired
    TaskRepository tasks;
    @Autowired
    SyncJobRepository jobs;
    @Autowired
    SyncRunRepository runs;
    @Autowired
    SyncQueue queue;
    @Autowired
    SyncWorker worker;
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
    void disconnecting_leaves_nothing_of_what_it_collected() {
        HttpHeaders owner = signIn("2001");
        ProjectEntity project = connected("2001", "201");
        collect(project, "rev-1");

        assertThat(documents.count()).isPositive();
        assertThat(assets.count()).isPositive();

        ResponseEntity<String> response = disconnect(owner, project, project.getFullName());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(projects.findById(project.getId())).isEmpty();
        assertThat(snapshots.count()).isZero();
        assertThat(documents.count()).isZero();
        assertThat(assets.count()).isZero();
        assertThat(tasks.count()).isZero();
        assertThat(jobs.count()).isZero();
        assertThat(runs.count()).isZero();
        // 가리키는 첨부가 없어진 내용은 함께 지운다.
        assertThat(assetContents.count()).isZero();
    }

    @Test
    void the_address_of_a_disconnected_project_answers_like_one_that_never_existed() {
        HttpHeaders owner = signIn("2002");
        ProjectEntity project = connected("2002", "202");
        collect(project, "rev-1");
        disconnect(owner, project, project.getFullName());

        ResponseEntity<String> after = rest.exchange("/api/v1/projects/" + project.getId(),
                HttpMethod.GET, new HttpEntity<>(null, owner), String.class);

        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(after.getBody()).contains("RESOURCE_NOT_FOUND");
    }

    @Test
    void a_name_that_does_not_match_changes_nothing() {
        HttpHeaders owner = signIn("2003");
        ProjectEntity project = connected("2003", "203");
        collect(project, "rev-1");

        ResponseEntity<String> response = disconnect(owner, project, "owner/다른저장소");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("fullName");
        // 되돌릴 수 없는 일이라 이름이 어긋나면 아무것도 지우지 않는다.
        assertThat(projects.findById(project.getId())).isPresent();
        assertThat(documents.count()).isPositive();
    }

    @Test
    void someone_who_cannot_manage_the_project_is_told_it_does_not_exist() {
        HttpHeaders owner = signIn("2004");
        ProjectEntity project = connected("2004", "204");
        HttpHeaders stranger = signIn("2005");
        ((FakeRepositoryAccessGateway) accessGateway).registerRepository("gho_2005",
                new GitHubRepository("204", project.getFullName(), false, "main", "11"));

        ResponseEntity<String> response = disconnect(stranger, project, project.getFullName());

        // 끊을 수 없는 사람에게는 있는지조차 알리지 않는다.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(projects.findById(project.getId())).isPresent();
    }

    @Test
    void the_picture_another_project_shares_does_not_disappear_with_it() {
        HttpHeaders owner = signIn("2006");
        ProjectEntity kept = connected("2006", "206");
        ProjectEntity removed = connected("2006", "207");
        // 같은 바이트다. 내용 해시가 열쇠라 두 게시본이 같은 행을 가리킨다.
        collect(kept, "rev-1");
        collect(removed, "rev-1");
        assertThat(assetContents.count()).isEqualTo(1);

        disconnect(owner, removed, removed.getFullName());

        assertThat(projects.findById(kept.getId())).isPresent();
        assertThat(documents.count()).isPositive();
        // 끊은 쪽 때문에 남은 쪽 그림이 깨지면 안 된다.
        assertThat(assetContents.count()).isEqualTo(1);
        assertThat(assets.count()).isEqualTo(1);
    }

    private ResponseEntity<String> disconnect(HttpHeaders actor, ProjectEntity project, String fullName) {
        return rest.exchange("/api/v1/projects/" + project.getId(), HttpMethod.DELETE,
                new HttpEntity<>("{\"fullName\":\"" + fullName + "\"}", actor), String.class);
    }

    private void collect(ProjectEntity project, String revision) {
        FakeRepositoryContentGateway contents = (FakeRepositoryContentGateway) contentGateway;
        contents.head(revision);
        contents.putFile(revision, "docs/guide.md",
                "# 안내서" + System.lineSeparator() + System.lineSeparator() + "![그림](image.png)");
        contents.putAsset(revision, "docs/image.png", PNG);
        queue.request(project.getId(), true);
        assertThat(worker.runOnce()).isTrue();
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
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        return headers;
    }

    private ProjectEntity connected(String githubUserId, String repositoryId) {
        UUID userId = users.findByGithubUserId(githubUserId).orElseThrow().getId();
        ProjectEntity project = fixture.newProject(repositoryId, userId);
        ((FakeRepositoryAccessGateway) accessGateway).registerRepository("gho_" + githubUserId,
                new GitHubRepository(repositoryId, project.getFullName(), false, "main", "11"));
        return project;
    }
}
