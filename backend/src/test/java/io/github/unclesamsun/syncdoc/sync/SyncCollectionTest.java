package io.github.unclesamsun.syncdoc.sync;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.document.domain.DocumentEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentRepository;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotRepository;
import io.github.unclesamsun.syncdoc.github.FakeRepositoryContentGateway;
import io.github.unclesamsun.syncdoc.github.GitHubLookupFailedException;
import io.github.unclesamsun.syncdoc.github.GitHubRateLimitedException;
import io.github.unclesamsun.syncdoc.github.RepositoryContentGateway;
import io.github.unclesamsun.syncdoc.project.domain.ProjectEntity;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import io.github.unclesamsun.syncdoc.support.ProjectFixture;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * 수집이 게시본을 만드는 과정과, 실패했을 때 마지막 정상 게시본이 지켜지는지 확인한다.
 *
 * <p>REQ-006의 인수 기준을 그대로 따라간다. 실패가 정상 데이터를 덮지 않을 것, 부분 수집을
 * 빈 목록이나 완료로 오인하지 않을 것, 바뀐 것이 없으면 다시 변환하지 않을 것.
 */
@Import(SyncCollectionTest.Gateways.class)
class SyncCollectionTest extends PostgresContainerSupport {

    @TestConfiguration
    static class Gateways {

        @Bean
        @Primary
        RepositoryContentGateway fakeContents() {
            return new FakeRepositoryContentGateway();
        }
    }

    @Autowired
    SyncWorker worker;
    @Autowired
    SyncQueue queue;
    @Autowired
    SyncStatusReader status;
    @Autowired
    ProjectRepository projects;
    @Autowired
    DocumentSnapshotRepository snapshots;
    @Autowired
    DocumentRepository documents;
    @Autowired
    ProjectFixture fixture;
    @Autowired
    RepositoryContentGateway gateway;

    private FakeRepositoryContentGateway fake() {
        return (FakeRepositoryContentGateway) gateway;
    }

    @BeforeEach
    void resetGateway() {
        fake().reset();
    }

    private UUID connectedProject(String repositoryId) {
        return fixture.newProject(repositoryId).getId();
    }

    /** 예약하고 한 번 실행한다. 실제 배포에서는 scheduler가 하는 일이다. */
    private void collect(UUID project) {
        queue.request(project, true);
        assertThat(worker.runOnce()).isTrue();
    }

    private UUID currentSnapshot(UUID project) {
        return projects.findById(project).orElseThrow().getCurrentSnapshotId();
    }

    @Test
    void the_first_collection_publishes_the_documents_it_found() {
        UUID project = connectedProject("401");
        fake().putFile("rev-1", "docs/guide.md", "---\nid: DOC-1\n---\n\n# 안내서\n\n본문");
        fake().putFile("rev-1", "docs/nested/spec.md", "# 명세\n\n표");
        fake().putFile("rev-1", "README.md", "# 문서 경로 밖");

        collect(project);

        UUID snapshot = currentSnapshot(project);
        assertThat(snapshot).isNotNull();
        assertThat(snapshots.findById(snapshot).orElseThrow().isComplete()).isTrue();
        List<DocumentEntity> stored = documents.findBySnapshotIdOrderByPath(snapshot);
        assertThat(stored).extracting(DocumentEntity::getPath)
                .containsExactly("docs/guide.md", "docs/nested/spec.md");
        assertThat(stored).extracting(DocumentEntity::getTitle).containsExactly("안내서", "명세");
        assertThat(status.statusOf(project).state()).isEqualTo("succeeded");
        assertThat(status.statusOf(project).lastSuccessAt()).isNotNull();
    }

    @Test
    void nothing_is_converted_again_when_the_revision_did_not_change() {
        UUID project = connectedProject("402");
        fake().putFile("rev-1", "docs/guide.md", "# 안내서");
        collect(project);
        UUID first = currentSnapshot(project);

        collect(project);

        assertThat(currentSnapshot(project)).isEqualTo(first);
        assertThat(snapshots.findByProjectIdOrderByCreatedAtDesc(project)).hasSize(1);
    }

    @Test
    void a_new_revision_becomes_the_current_snapshot_and_the_old_one_remains() {
        UUID project = connectedProject("403");
        fake().putFile("rev-1", "docs/guide.md", "# 안내서");
        collect(project);
        UUID first = currentSnapshot(project);

        fake().putFile("rev-2", "docs/guide.md", "# 고친 안내서");
        fake().head("rev-2");
        collect(project);

        UUID second = currentSnapshot(project);
        assertThat(second).isNotEqualTo(first);
        assertThat(snapshots.findById(first)).isPresent();
        assertThat(documents.findBySnapshotIdOrderByPath(second))
                .extracting(DocumentEntity::getTitle).containsExactly("고친 안내서");
    }

    @Test
    void a_document_deleted_in_the_new_revision_is_not_in_the_new_snapshot() {
        UUID project = connectedProject("404");
        fake().putFile("rev-1", "docs/keep.md", "# 남는 문서");
        fake().putFile("rev-1", "docs/gone.md", "# 사라질 문서");
        collect(project);

        fake().putFile("rev-2", "docs/keep.md", "# 남는 문서");
        fake().head("rev-2");
        collect(project);

        assertThat(documents.findBySnapshotIdOrderByPath(currentSnapshot(project)))
                .extracting(DocumentEntity::getPath).containsExactly("docs/keep.md");
    }

    @Test
    void a_failed_refresh_keeps_the_last_good_snapshot() {
        UUID project = connectedProject("405");
        fake().putFile("rev-1", "docs/guide.md", "# 안내서");
        collect(project);
        UUID good = currentSnapshot(project);

        fake().putFile("rev-2", "docs/guide.md", "# 새 안내서");
        fake().head("rev-2");
        fake().failReadWith(new GitHubLookupFailedException("본문을 읽지 못했다"));
        collect(project);

        assertThat(currentSnapshot(project)).isEqualTo(good);
        assertThat(documents.findBySnapshotIdOrderByPath(good))
                .extracting(DocumentEntity::getTitle).containsExactly("안내서");
        SyncStatusReader.SyncStatus after = status.statusOf(project);
        assertThat(after.state()).isEqualTo("failed");
        assertThat(after.errorCode()).isEqualTo("GITHUB_UNAVAILABLE");
        assertThat(after.lastSuccessAt()).isNotNull();
        assertThat(after.nextRetryAt()).isNotNull();
    }

    @Test
    void a_half_written_snapshot_is_never_marked_complete() {
        UUID project = connectedProject("406");
        fake().putFile("rev-1", "docs/a.md", "# 하나");
        fake().putFile("rev-1", "docs/b.md", "# 둘");
        // 첫 문서를 읽은 뒤 실패한다. 게시본은 절반만 찬 상태로 남는다.
        fake().onRead(count -> {
            if (count == 1) {
                fake().failReadWith(new GitHubLookupFailedException("끊겼다"));
            }
        });

        collect(project);

        assertThat(currentSnapshot(project)).isNull();
        List<DocumentSnapshotEntity> all = snapshots.findByProjectIdOrderByCreatedAtDesc(project);
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().isComplete()).isFalse();
    }

    @Test
    void a_retry_after_a_partial_collection_refills_the_same_snapshot() {
        UUID project = connectedProject("407");
        fake().putFile("rev-1", "docs/a.md", "# 하나");
        fake().putFile("rev-1", "docs/b.md", "# 둘");
        fake().onRead(count -> {
            if (count == 1) {
                fake().failReadWith(new GitHubLookupFailedException("끊겼다"));
            }
        });
        collect(project);

        fake().onRead(count -> {
        });
        fake().failReadWith(null);
        collect(project);

        UUID snapshot = currentSnapshot(project);
        assertThat(snapshot).isNotNull();
        assertThat(documents.findBySnapshotIdOrderByPath(snapshot))
                .extracting(DocumentEntity::getPath).containsExactly("docs/a.md", "docs/b.md");
        assertThat(snapshots.findByProjectIdOrderByCreatedAtDesc(project)).hasSize(1);
    }

    @Test
    void a_first_collection_that_fails_leaves_no_snapshot_and_an_error() {
        UUID project = connectedProject("408");
        fake().failWith(new GitHubLookupFailedException("연결하지 못했다"));

        collect(project);

        assertThat(currentSnapshot(project)).isNull();
        SyncStatusReader.SyncStatus after = status.statusOf(project);
        assertThat(after.state()).isEqualTo("failed");
        assertThat(after.lastSuccessAt()).isNull();
        assertThat(after.errorCode()).isEqualTo("GITHUB_UNAVAILABLE");
    }

    @Test
    void a_missing_docs_root_is_reported_as_its_own_error() {
        UUID project = connectedProject("409");
        fake().docsRoot("documents");

        collect(project);

        assertThat(status.statusOf(project).errorCode()).isEqualTo("DOCS_ROOT_MISSING");
    }

    @Test
    void a_rate_limited_collection_waits_until_github_says() {
        UUID project = connectedProject("410");
        // PostgreSQL의 timestamptz는 마이크로초까지 보관한다. 나노초를 그대로 두면 비교가 어긋난다.
        Instant retryAt = Instant.now().plusSeconds(1800).truncatedTo(ChronoUnit.MICROS);
        fake().failWith(new GitHubRateLimitedException(retryAt));

        collect(project);

        SyncStatusReader.SyncStatus after = status.statusOf(project);
        assertThat(after.errorCode()).isEqualTo("GITHUB_RATE_LIMITED");
        assertThat(after.nextRetryAt()).isEqualTo(retryAt);
    }

    @Test
    void a_push_during_a_run_is_collected_by_the_job_that_follows() {
        UUID project = connectedProject("411");
        fake().putFile("rev-1", "docs/a.md", "# 하나");
        fake().putFile("rev-2", "docs/a.md", "# 하나");
        fake().putFile("rev-2", "docs/b.md", "# 둘");
        // 첫 문서를 읽는 사이에 저장소가 바뀌고 이벤트가 들어온다.
        fake().onRead(count -> {
            if (count == 1) {
                fake().head("rev-2");
                queue.request(project, false);
            }
        });

        collect(project);
        assertThat(documents.findBySnapshotIdOrderByPath(currentSnapshot(project)))
                .extracting(DocumentEntity::getPath).containsExactly("docs/a.md");

        // 실행 중에 들어온 이벤트 때문에 뒤따르는 작업이 생겼고, 그 작업이 새 revision을 게시한다.
        assertThat(worker.runOnce()).isTrue();
        assertThat(documents.findBySnapshotIdOrderByPath(currentSnapshot(project)))
                .extracting(DocumentEntity::getPath).containsExactly("docs/a.md", "docs/b.md");
    }

    @Test
    void a_connected_project_starts_with_a_waiting_collection() {
        ProjectEntity project = fixture.newProject("412");

        // 연결 직후에는 게시본이 없다. UI-007이 이 상태를 첫 수집 대기로 보여준다.
        queue.request(project.getId(), true);

        assertThat(currentSnapshot(project.getId())).isNull();
        assertThat(status.statusOf(project.getId()).state()).isEqualTo("queued");
        assertThat(status.statusOf(project.getId()).pending()).isTrue();
    }
}
