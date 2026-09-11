package io.github.unclesamsun.syncdoc.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.unclesamsun.syncdoc.document.domain.DocumentEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentRepository;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotRepository;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import io.github.unclesamsun.syncdoc.support.ProjectFixture;
import io.github.unclesamsun.syncdoc.sync.domain.SyncJobEntity;
import io.github.unclesamsun.syncdoc.sync.domain.SyncJobRepository;
import io.github.unclesamsun.syncdoc.sync.domain.WebhookDeliveryEntity;
import io.github.unclesamsun.syncdoc.sync.domain.WebhookDeliveryRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 게시본과 작업 큐의 제약을 DB가 실제로 지키는지 확인한다. 애플리케이션 코드의 판단만으로는
 * 동시 요청을 막지 못하므로 제약 자체가 검사 대상이다.
 */
class SyncSchemaTest extends PostgresContainerSupport {

    @Autowired
    SyncJobRepository jobs;
    @Autowired
    DocumentSnapshotRepository snapshots;
    @Autowired
    DocumentRepository documents;
    @Autowired
    WebhookDeliveryRepository deliveries;
    @Autowired
    ProjectFixture fixture;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void a_project_cannot_have_two_active_jobs_of_the_same_kind() {
        UUID project = fixture.newProject("201").getId();
        Instant now = Instant.now();
        jobs.saveAndFlush(SyncJobEntity.queued(project, now, now));

        assertThatThrownBy(() -> jobs.saveAndFlush(SyncJobEntity.queued(project, now, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_finished_job_does_not_block_the_next_one() {
        UUID project = fixture.newProject("202").getId();
        Instant now = Instant.now();
        SyncJobEntity first = jobs.saveAndFlush(SyncJobEntity.queued(project, now, now));
        first.claim(now, now.plusSeconds(120));
        first.succeed(now);
        jobs.saveAndFlush(first);

        assertThat(jobs.saveAndFlush(SyncJobEntity.queued(project, now, now)).getId()).isNotNull();
    }

    @Test
    void the_same_revision_is_published_only_once_per_renderer_and_policy() {
        UUID project = fixture.newProject("203").getId();
        snapshots.saveAndFlush(new DocumentSnapshotEntity(project, "abc123", "r0", "p0", Instant.now()));

        assertThatThrownBy(() -> snapshots.saveAndFlush(
                new DocumentSnapshotEntity(project, "abc123", "r0", "p0", Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void a_changed_renderer_version_makes_a_new_snapshot_of_the_same_revision() {
        UUID project = fixture.newProject("204").getId();
        snapshots.saveAndFlush(new DocumentSnapshotEntity(project, "abc123", "r0", "p0", Instant.now()));

        assertThat(snapshots.saveAndFlush(
                new DocumentSnapshotEntity(project, "abc123", "r1", "p0", Instant.now())).getId()).isNotNull();
    }

    @Test
    void a_project_cannot_point_at_another_projects_snapshot() {
        UUID mine = fixture.newProject("205").getId();
        UUID other = fixture.newProject("206").getId();
        UUID otherSnapshot = snapshots.saveAndFlush(
                new DocumentSnapshotEntity(other, "abc123", "r0", "p0", Instant.now())).getId();

        assertThatThrownBy(() -> jdbc.update(
                "update projects set current_snapshot_id = ? where id = ?", otherSnapshot, mine))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void the_same_path_appears_once_in_a_snapshot() {
        UUID project = fixture.newProject("207").getId();
        UUID snapshot = snapshots.saveAndFlush(
                new DocumentSnapshotEntity(project, "abc123", "r0", "p0", Instant.now())).getId();
        documents.saveAndFlush(new DocumentEntity(snapshot, "docs/a.md", "A", "h1", "본문"));

        assertThatThrownBy(() -> documents.saveAndFlush(
                new DocumentEntity(snapshot, "docs/a.md", "A", "h2", "다른 본문")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void the_same_delivery_is_stored_once() {
        Instant now = Instant.now();
        deliveries.saveAndFlush(new WebhookDeliveryEntity("d-1", "push", now));

        assertThatThrownBy(() -> jdbc.update(
                "insert into webhook_deliveries (delivery_id, event, received_at) values (?, ?, ?)",
                "d-1", "push", java.sql.Timestamp.from(now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
