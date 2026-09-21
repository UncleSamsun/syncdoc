package io.github.unclesamsun.syncdoc.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.auth.CsrfTokenFilter;
import io.github.unclesamsun.syncdoc.auth.SessionService;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.crypto.TokenCipher;
import io.github.unclesamsun.syncdoc.document.domain.AssetContentRepository;
import io.github.unclesamsun.syncdoc.document.domain.AssetEntity;
import io.github.unclesamsun.syncdoc.document.domain.AssetRepository;
import io.github.unclesamsun.syncdoc.document.domain.DocumentEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentRepository;
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
import java.nio.charset.StandardCharsets;
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
 * 첨부 수집과 API-020. 무엇을 담지 않는지가 담는 것만큼 중요하다 — 실행될 수 있는 형식,
 * 이름과 내용이 다른 파일, 상한을 넘는 크기는 담지 않는다.
 */
@Import(AssetApiTest.Gateways.class)
class AssetApiTest extends PostgresContainerSupport {

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

    /** PNG 앞머리 여덟 바이트 뒤에 아무 내용이나 붙인 최소 fixture. */
    private static byte[] png(int size) {
        byte[] bytes = new byte[size];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
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
    DocumentRepository documents;
    @Autowired
    AssetRepository assets;
    @Autowired
    AssetContentRepository assetContents;
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

    private UUID currentSnapshot(UUID projectId) {
        return projects.findById(projectId).orElseThrow().getCurrentSnapshotId();
    }

    private ResponseEntity<byte[]> getBytes(Actor actor, String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(null, actor.headers()), byte[].class);
    }

    @Test
    void a_collected_picture_is_served_with_the_type_it_was_stored_as() {
        Actor owner = actor("8001", "gho_a");
        ProjectEntity project = projectVisibleTo(owner, "gho_a", "801");
        contents().putFile("rev-1", "docs/guide.md", "# 안내서\n\n![구성도](./images/a.png)");
        contents().putAsset("rev-1", "docs/images/a.png", png(64));
        collect(project.getId());

        UUID snapshot = currentSnapshot(project.getId());
        AssetEntity asset = assets.findBySnapshotIdOrderByPath(snapshot).getFirst();
        DocumentEntity document = documents.findBySnapshotIdOrderByPath(snapshot).getFirst();

        // 문서가 가리키는 주소가 곧 이 계약의 주소다.
        assertThat(document.getHtml()).contains("/assets/" + asset.getId() + "?snapshotId=" + snapshot);

        ResponseEntity<byte[]> response = getBytes(owner, "/api/v1/projects/" + project.getId()
                + "/assets/" + asset.getId() + "?snapshotId=" + snapshot);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(64);
        assertThat(response.getHeaders().getFirst("Content-Type")).isEqualTo("image/png");
        // 브라우저가 내용을 보고 형식을 새로 추측하지 못하게 한다.
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void a_request_without_a_snapshot_is_refused() {
        Actor owner = actor("8002", "gho_b");
        ProjectEntity project = projectVisibleTo(owner, "gho_b", "802");
        contents().putFile("rev-1", "docs/guide.md", "# 안내서");
        contents().putAsset("rev-1", "docs/images/a.png", png(32));
        collect(project.getId());
        UUID assetId = assets.findBySnapshotIdOrderByPath(currentSnapshot(project.getId())).getFirst().getId();

        assertThat(getBytes(owner, "/api/v1/projects/" + project.getId() + "/assets/" + assetId)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void someone_who_cannot_see_the_project_cannot_load_its_pictures() {
        Actor owner = actor("8003", "gho_c");
        Actor stranger = actor("8004", "gho_d");
        ProjectEntity project = projectVisibleTo(owner, "gho_c", "803");
        contents().putFile("rev-1", "docs/guide.md", "# 안내서");
        contents().putAsset("rev-1", "docs/images/a.png", png(32));
        collect(project.getId());
        UUID snapshot = currentSnapshot(project.getId());
        UUID assetId = assets.findBySnapshotIdOrderByPath(snapshot).getFirst().getId();

        assertThat(getBytes(stranger, "/api/v1/projects/" + project.getId()
                + "/assets/" + assetId + "?snapshotId=" + snapshot).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void a_picture_from_another_snapshot_is_not_reachable_through_this_one() {
        Actor owner = actor("8005", "gho_e");
        ProjectEntity mine = projectVisibleTo(owner, "gho_e", "804");
        ProjectEntity other = projectVisibleTo(owner, "gho_e", "805");
        contents().putFile("rev-1", "docs/guide.md", "# 안내서");
        contents().putAsset("rev-1", "docs/images/a.png", png(32));
        collect(mine.getId());
        collect(other.getId());

        UUID mySnapshot = currentSnapshot(mine.getId());
        UUID otherAsset = assets.findBySnapshotIdOrderByPath(currentSnapshot(other.getId())).getFirst().getId();

        assertThat(getBytes(owner, "/api/v1/projects/" + mine.getId()
                + "/assets/" + otherAsset + "?snapshotId=" + mySnapshot).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void a_snapshot_that_no_longer_exists_is_gone() {
        Actor owner = actor("8006", "gho_f");
        ProjectEntity project = projectVisibleTo(owner, "gho_f", "806");
        contents().putFile("rev-1", "docs/guide.md", "# 안내서");
        contents().putAsset("rev-1", "docs/images/a.png", png(32));
        collect(project.getId());
        UUID assetId = assets.findBySnapshotIdOrderByPath(currentSnapshot(project.getId())).getFirst().getId();

        assertThat(getBytes(owner, "/api/v1/projects/" + project.getId()
                + "/assets/" + assetId + "?snapshotId=" + UUID.randomUUID()).getStatusCode())
                .isEqualTo(HttpStatus.GONE);
    }

    @Test
    void a_format_that_can_carry_a_script_is_never_collected() {
        Actor owner = actor("8007", "gho_g");
        ProjectEntity project = projectVisibleTo(owner, "gho_g", "807");
        contents().putFile("rev-1", "docs/guide.md", "# 안내서\n\n![그림](./images/a.svg)");
        contents().putAsset("rev-1", "docs/images/a.svg",
                "<svg onload=\"alert(1)\"></svg>".getBytes(StandardCharsets.UTF_8));
        collect(project.getId());

        UUID snapshot = currentSnapshot(project.getId());
        assertThat(assets.findBySnapshotIdOrderByPath(snapshot)).isEmpty();
        DocumentEntity document = documents.findBySnapshotIdOrderByPath(snapshot).getFirst();
        assertThat(document.getHtml()).doesNotContain("<img").doesNotContain("svg");
        assertThat(document.getWarningsJson()).contains("ASSET_NOT_AVAILABLE");
    }

    @Test
    void a_file_whose_name_does_not_match_its_content_is_not_collected() {
        Actor owner = actor("8008", "gho_h");
        ProjectEntity project = projectVisibleTo(owner, "gho_h", "808");
        contents().putFile("rev-1", "docs/guide.md", "# 안내서");
        contents().putAsset("rev-1", "docs/images/fake.png",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
        collect(project.getId());

        assertThat(assets.findBySnapshotIdOrderByPath(currentSnapshot(project.getId()))).isEmpty();
    }

    @Test
    void a_picture_over_the_limit_is_skipped_without_stopping_the_collection() {
        Actor owner = actor("8009", "gho_i");
        ProjectEntity project = projectVisibleTo(owner, "gho_i", "809");
        contents().putFile("rev-1", "docs/guide.md", "# 안내서");
        contents().putAsset("rev-1", "docs/images/small.png", png(32));
        contents().putAsset("rev-1", "docs/images/huge.png", png(11 * 1024 * 1024));
        collect(project.getId());

        UUID snapshot = currentSnapshot(project.getId());
        assertThat(snapshot).isNotNull();
        assertThat(assets.findBySnapshotIdOrderByPath(snapshot))
                .extracting(AssetEntity::getPath).containsExactly("docs/images/small.png");
    }

    @Test
    void the_same_picture_in_two_snapshots_is_stored_once() {
        Actor owner = actor("8010", "gho_j");
        ProjectEntity project = projectVisibleTo(owner, "gho_j", "810");
        contents().putFile("rev-1", "docs/guide.md", "# 안내서");
        contents().putAsset("rev-1", "docs/images/a.png", png(64));
        collect(project.getId());

        contents().putFile("rev-2", "docs/guide.md", "# 고친 안내서");
        contents().putAsset("rev-2", "docs/images/a.png", png(64));
        contents().head("rev-2");
        collect(project.getId());

        List<AssetEntity> all = assets.findAll();
        assertThat(all).hasSize(2);
        assertThat(all).extracting(AssetEntity::getStorageKey).containsOnly(all.getFirst().getStorageKey());
        assertThat(assetContents.count()).isEqualTo(1);
    }
}
