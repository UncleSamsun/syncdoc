package io.github.unclesamsun.syncdoc.sync;

import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotRepository;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import io.github.unclesamsun.syncdoc.sync.domain.SyncJobEntity;
import io.github.unclesamsun.syncdoc.sync.domain.SyncJobRepository;
import io.github.unclesamsun.syncdoc.sync.domain.SyncRunEntity;
import io.github.unclesamsun.syncdoc.sync.domain.SyncRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집 작업의 예약·점유·종료를 한 곳에서 다룬다.
 *
 * <p>같은 프로젝트의 활성 작업은 하나뿐이라는 규칙은 DB의 partial unique가 지키고, 이 클래스는
 * 그 제약에 걸렸을 때 기존 작업으로 합친다. 미리 조회해서 없으면 만드는 방식만으로는
 * 동시에 도착한 중복 이벤트를 막지 못한다.
 *
 * <p>결과를 남길 자격은 임대 token이 정한다. 임대가 만료되어 다른 worker가 같은 작업을 다시 잡으면
 * 오래된 worker의 완료·실패 보고는 모두 거부된다.
 */
@Service
public class SyncQueue {

    private static final List<String> ACTIVE = List.of(SyncJobEntity.QUEUED, SyncJobEntity.RUNNING);

    private final SyncJobRepository jobs;
    private final SyncRunRepository runs;
    private final DocumentSnapshotRepository snapshots;
    private final ProjectRepository projects;
    private final RetryPolicy retryPolicy;
    private final SyncProperties properties;
    private final Clock clock;

    public SyncQueue(SyncJobRepository jobs, SyncRunRepository runs, DocumentSnapshotRepository snapshots,
                     ProjectRepository projects, RetryPolicy retryPolicy, SyncProperties properties,
                     Clock clock) {
        this.jobs = jobs;
        this.runs = runs;
        this.snapshots = snapshots;
        this.projects = projects;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
        this.clock = clock;
    }

    /** @param reused 새 작업을 만들지 않고 이미 있던 작업에 합쳤다는 뜻이다 */
    public record Scheduled(UUID jobId, boolean reused) {
    }

    /** worker가 받은 점유권. {@code token} 없이는 어떤 결과도 남길 수 없다. */
    public record Lease(UUID jobId, UUID projectId, UUID runId, UUID token, int attempt) {
    }

    /**
     * 수집을 예약한다.
     *
     * @param expedite 사용자가 직접 요청한 경우다. 기다리고 있던 재시도를 앞당긴다.
     *                 이벤트로 들어온 예약은 이미 잡힌 재시도 간격을 흔들지 않는다.
     */
    @Transactional
    public Scheduled request(UUID projectId, boolean expedite) {
        Instant now = clock.instant();
        Optional<SyncJobEntity> active = activeJob(projectId);
        if (active.isPresent()) {
            return new Scheduled(mergeInto(active.get(), expedite, now).getId(), true);
        }
        try {
            return new Scheduled(jobs.saveAndFlush(SyncJobEntity.queued(projectId, now, now)).getId(), false);
        } catch (DataIntegrityViolationException e) {
            // 같은 순간에 다른 요청이 먼저 만들었다. 그 작업에 합친다.
            SyncJobEntity winner = activeJob(projectId).orElseThrow(() -> e);
            return new Scheduled(mergeInto(winner, expedite, now).getId(), true);
        }
    }

    private SyncJobEntity mergeInto(SyncJobEntity job, boolean expedite, Instant now) {
        if (SyncJobEntity.RUNNING.equals(job.getState())) {
            // 수집 도중에 원문이 또 바뀌었을 수 있다. 지금 결과를 버리지 않고 끝난 뒤 다시 확인한다.
            job.requestRerun(now);
        } else if (expedite && job.getDueAt().isAfter(now)) {
            job.retryAt(now, job.getLastErrorCode(), now);
        }
        return jobs.saveAndFlush(job);
    }

    @Transactional(readOnly = true)
    public Optional<SyncJobEntity> activeJob(UUID projectId) {
        return jobs.findFirstByProjectIdAndKindAndStateIn(projectId, SyncJobEntity.KIND_COLLECT, ACTIVE);
    }

    /**
     * 실행할 작업을 하나 잡는다. 예정 시각이 된 작업을 먼저 보고, 없으면 임대가 끊긴 작업을 회수한다.
     * 회수 경로가 재시작 후 재개를 담당한다.
     */
    @Transactional
    public Optional<Lease> claimNext() {
        Instant now = clock.instant();
        Optional<UUID> due = jobs.lockNextDue(now);
        if (due.isPresent()) {
            return due.map(id -> lease(id, now, false));
        }
        return jobs.lockExpiredLease(now).map(id -> lease(id, now, true));
    }

    private Lease lease(UUID jobId, Instant now, boolean reclaimed) {
        SyncJobEntity job = jobs.findById(jobId).orElseThrow();
        if (reclaimed) {
            // 앞선 worker의 시도는 결과를 남기지 못한 채 끝났다. 그 사실을 이력에 남긴다.
            runs.findFirstByProjectIdOrderByStartedAtDesc(job.getProjectId())
                    .filter(run -> SyncRunEntity.RUNNING.equals(run.getOutcome()))
                    .ifPresent(run -> run.failed("LEASE_EXPIRED", now, "{}"));
        }
        UUID token = job.claim(now, now.plus(properties.leaseDuration()));
        jobs.saveAndFlush(job);
        SyncRunEntity run = runs.saveAndFlush(new SyncRunEntity(job.getProjectId(), job.getId(), now));
        return new Lease(job.getId(), job.getProjectId(), run.getId(), token, job.getAttempt());
    }

    /** 어떤 revision을 대상으로 도는 중인지 남긴다. 진행 중 작업을 밖에서 확인할 때 쓴다. */
    @Transactional
    public void rememberTargetRevision(Lease lease, String revision) {
        jobs.findById(lease.jobId())
                .filter(job -> job.owns(lease.token()))
                .ifPresent(job -> {
                    job.rememberTargetRevision(revision, clock.instant());
                    jobs.saveAndFlush(job);
                });
    }

    /** 수집이 길어지는 동안 임대를 연장한다. 연장에 실패하면 이미 소유권을 잃은 것이다. */
    @Transactional
    public boolean renew(UUID jobId, UUID token) {
        Instant now = clock.instant();
        return jobs.findById(jobId)
                .map(job -> {
                    boolean renewed = job.renewLease(token, now, now.plus(properties.leaseDuration()));
                    if (renewed) {
                        jobs.saveAndFlush(job);
                    }
                    return renewed;
                })
                .orElse(false);
    }

    /**
     * 모은 문서를 게시하고 작업을 끝낸다. 임대 확인·완료 표시·현재 게시본 교체가 한 트랜잭션이다.
     * 나누면 임대를 잃은 worker가 확인과 교체 사이에 끼어들어 오래된 결과를 게시할 수 있다.
     *
     * @return 소유권이 없으면 false. 이때는 아무것도 게시하지 않는다
     */
    @Transactional
    public boolean publish(Lease lease, UUID snapshotId, String sourceRevision, String diagnosticsJson) {
        Instant now = clock.instant();
        SyncJobEntity job = ownedJob(lease);
        if (job == null) {
            return false;
        }
        DocumentSnapshotEntity snapshot = snapshots.findById(snapshotId).orElseThrow();
        snapshot.markComplete();
        snapshots.saveAndFlush(snapshot);
        projects.publishSnapshot(job.getProjectId(), snapshotId);
        finish(job, lease, sourceRevision, diagnosticsJson, now);
        return true;
    }

    /**
     * 바뀐 것이 없어 게시본을 새로 만들지 않고 끝낸다. 원문·변환 규칙이 그대로면 다시 변환하지 않는다.
     *
     * @return 소유권이 없으면 false
     */
    @Transactional
    public boolean succeedUnchanged(Lease lease, String sourceRevision, String diagnosticsJson) {
        Instant now = clock.instant();
        SyncJobEntity job = ownedJob(lease);
        if (job == null) {
            return false;
        }
        finish(job, lease, sourceRevision, diagnosticsJson, now);
        return true;
    }

    /** 실행 중에 들어온 이벤트는 이 시점에 새 작업이 된다. 그 전에 만들면 활성 작업이 둘이 된다. */
    private void finish(SyncJobEntity job, Lease lease, String sourceRevision, String diagnosticsJson,
                        Instant now) {
        boolean rerun = job.isRerunRequested();
        job.clearRerunRequest(now);
        job.succeed(now);
        jobs.saveAndFlush(job);
        runs.findById(lease.runId()).ifPresent(run -> run.succeeded(sourceRevision, now, diagnosticsJson));
        if (rerun) {
            jobs.saveAndFlush(SyncJobEntity.queued(job.getProjectId(), now, now));
        }
    }

    /**
     * 실패로 끝내고 다음 시도를 예약한다. 실패한 시도는 게시본을 바꾸지 않았으므로
     * 마지막 정상 snapshot은 그대로 남는다.
     *
     * @param githubRetryAfter GitHub가 알려준 재시도 시각. 없으면 null
     * @return 소유권이 없으면 false
     */
    @Transactional
    public boolean fail(Lease lease, String errorCode, Instant githubRetryAfter, String diagnosticsJson) {
        Instant now = clock.instant();
        SyncJobEntity job = ownedJob(lease);
        if (job == null) {
            return false;
        }
        job.retryAt(retryPolicy.nextAttemptAt(job.getAttempt(), now, githubRetryAfter), errorCode, now);
        jobs.saveAndFlush(job);
        runs.findById(lease.runId()).ifPresent(run -> run.failed(errorCode, now, diagnosticsJson));
        return true;
    }

    /** 작업 행을 잠그고 임대를 확인한다. 임대를 잃었으면 null이고 호출자의 결과는 버려진다. */
    private SyncJobEntity ownedJob(Lease lease) {
        SyncJobEntity job = jobs.findAndLockById(lease.jobId()).orElse(null);
        return job != null && job.owns(lease.token()) ? job : null;
    }

    @Transactional(readOnly = true)
    public Optional<SyncRunEntity> lastRun(UUID projectId) {
        return runs.findFirstByProjectIdOrderByStartedAtDesc(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<SyncRunEntity> lastSuccessfulRun(UUID projectId) {
        return runs.findFirstByProjectIdAndOutcomeOrderByFinishedAtDesc(projectId, SyncRunEntity.SUCCEEDED);
    }
}
