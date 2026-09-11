package io.github.unclesamsun.syncdoc.sync;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.support.MutableClock;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import io.github.unclesamsun.syncdoc.support.ProjectFixture;
import io.github.unclesamsun.syncdoc.sync.domain.SyncJobEntity;
import io.github.unclesamsun.syncdoc.sync.domain.SyncJobRepository;
import io.github.unclesamsun.syncdoc.sync.domain.SyncRunEntity;
import io.github.unclesamsun.syncdoc.sync.domain.SyncRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 작업 큐가 중복·동시 실행·중단을 어떻게 다루는지 검사한다.
 *
 * <p>여기서 확인하는 것은 하나다. 어떤 순서로 꼬여도 게시본은 하나로 남고, 오래된 실행이
 * 새 결과를 덮지 못한다.
 */
@Import(SyncRecoveryTest.FixedClock.class)
class SyncRecoveryTest extends PostgresContainerSupport {

    @TestConfiguration
    static class FixedClock {

        @Bean
        @Primary
        Clock testClock() {
            return new MutableClock(Instant.parse("2026-09-11T00:00:00Z"));
        }
    }

    @Autowired
    SyncQueue queue;
    @Autowired
    SyncJobRepository jobs;
    @Autowired
    SyncRunRepository runs;
    @Autowired
    ProjectFixture fixture;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    @BeforeEach
    void resetClock() {
        clock().set(Instant.parse("2026-09-11T00:00:00Z"));
    }

    @Test
    void a_second_request_joins_the_job_that_is_already_waiting() {
        UUID project = fixture.newProject("301").getId();

        SyncQueue.Scheduled first = queue.request(project, false);
        SyncQueue.Scheduled second = queue.request(project, false);

        assertThat(second.jobId()).isEqualTo(first.jobId());
        assertThat(second.reused()).isTrue();
        assertThat(jobs.findByProjectIdOrderByCreatedAtDesc(project)).hasSize(1);
    }

    @Test
    void only_one_worker_gets_a_job_when_two_claim_at_once() throws Exception {
        UUID project = fixture.newProject("302").getId();
        queue.request(project, false);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Optional<SyncQueue.Lease>> claim = () -> queue.claimNext();
            List<Future<Optional<SyncQueue.Lease>>> results = pool.invokeAll(List.of(claim, claim));
            long taken = results.stream().filter(future -> get(future).isPresent()).count();

            assertThat(taken).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void an_expired_lease_is_picked_up_again_so_a_restart_resumes_the_work() {
        UUID project = fixture.newProject("303").getId();
        queue.request(project, false);
        SyncQueue.Lease first = queue.claimNext().orElseThrow();

        expireLease(first.jobId());
        SyncQueue.Lease second = queue.claimNext().orElseThrow();

        assertThat(second.jobId()).isEqualTo(first.jobId());
        assertThat(second.token()).isNotEqualTo(first.token());
        assertThat(second.attempt()).isEqualTo(2);
        // 앞선 시도는 결과 없이 끝난 것으로 남는다. 실행 중인 채로 영원히 남지 않는다.
        assertThat(runs.findById(first.runId()).orElseThrow().getOutcome())
                .isEqualTo(SyncRunEntity.FAILED);
    }

    @Test
    void a_worker_that_lost_its_lease_cannot_publish() {
        UUID project = fixture.newProject("304").getId();
        queue.request(project, false);
        SyncQueue.Lease stale = queue.claimNext().orElseThrow();
        expireLease(stale.jobId());
        queue.claimNext().orElseThrow();

        UUID snapshot = insertSnapshot(project, "rev-old");
        boolean published = queue.publish(stale, snapshot, "rev-old", "{}");

        assertThat(published).isFalse();
        assertThat(currentSnapshotOf(project)).isNull();
    }

    @Test
    void a_stale_worker_cannot_report_a_failure_either() {
        UUID project = fixture.newProject("305").getId();
        queue.request(project, false);
        SyncQueue.Lease stale = queue.claimNext().orElseThrow();
        expireLease(stale.jobId());
        SyncQueue.Lease current = queue.claimNext().orElseThrow();

        assertThat(queue.fail(stale, "GITHUB_UNAVAILABLE", null, "{}")).isFalse();
        assertThat(jobs.findById(current.jobId()).orElseThrow().getState())
                .isEqualTo(SyncJobEntity.RUNNING);
    }

    @Test
    void an_event_during_a_run_becomes_a_new_job_after_that_run_ends() {
        UUID project = fixture.newProject("306").getId();
        queue.request(project, false);
        SyncQueue.Lease lease = queue.claimNext().orElseThrow();

        SyncQueue.Scheduled during = queue.request(project, false);
        assertThat(during.reused()).isTrue();
        assertThat(jobs.findById(lease.jobId()).orElseThrow().isRerunRequested()).isTrue();

        queue.succeedUnchanged(lease, "rev-1", "{}");

        List<SyncJobEntity> all = jobs.findByProjectIdOrderByCreatedAtDesc(project);
        assertThat(all).hasSize(2);
        assertThat(queue.activeJob(project)).isPresent();
    }

    @Test
    void a_failed_attempt_waits_at_least_the_minimum_and_at_most_the_maximum() {
        UUID project = fixture.newProject("307").getId();
        queue.request(project, false);

        SyncQueue.Lease first = queue.claimNext().orElseThrow();
        queue.fail(first, "GITHUB_UNAVAILABLE", null, "{}");
        Instant afterFirst = jobs.findById(first.jobId()).orElseThrow().getDueAt();
        assertThat(afterFirst).isEqualTo(clock.instant().plus(Duration.ofSeconds(60)));

        // 실패가 거듭되면 간격이 늘지만 상한을 넘지 않는다.
        for (int attempt = 2; attempt <= 8; attempt++) {
            clock().advance(Duration.ofHours(1));
            SyncQueue.Lease lease = queue.claimNext().orElseThrow();
            queue.fail(lease, "GITHUB_UNAVAILABLE", null, "{}");
        }
        Instant last = jobs.findById(first.jobId()).orElseThrow().getDueAt();
        assertThat(last).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
    }

    @Test
    void the_retry_time_github_asked_for_wins_over_ours() {
        UUID project = fixture.newProject("308").getId();
        queue.request(project, false);
        SyncQueue.Lease lease = queue.claimNext().orElseThrow();
        Instant githubSaid = clock.instant().plus(Duration.ofMinutes(42));

        queue.fail(lease, "GITHUB_RATE_LIMITED", githubSaid, "{}");

        assertThat(jobs.findById(lease.jobId()).orElseThrow().getDueAt()).isEqualTo(githubSaid);
    }

    @Test
    void a_user_request_pulls_a_waiting_retry_forward() {
        UUID project = fixture.newProject("309").getId();
        queue.request(project, false);
        SyncQueue.Lease lease = queue.claimNext().orElseThrow();
        queue.fail(lease, "GITHUB_UNAVAILABLE", null, "{}");

        queue.request(project, true);

        assertThat(jobs.findById(lease.jobId()).orElseThrow().getDueAt()).isEqualTo(clock.instant());
    }

    private void expireLease(UUID jobId) {
        jdbc.update("update sync_jobs set lease_until = ? where id = ?",
                java.sql.Timestamp.from(clock.instant().minusSeconds(1)), jobId);
    }

    private UUID insertSnapshot(UUID project, String revision) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into document_snapshots
                    (id, project_id, source_revision, renderer_version, policy_version, created_at, complete)
                values (?, ?, ?, 'none', 'none', now(), false)
                """, id, project, revision);
        return id;
    }

    private UUID currentSnapshotOf(UUID project) {
        return jdbc.queryForObject("select current_snapshot_id from projects where id = ?",
                UUID.class, project);
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
