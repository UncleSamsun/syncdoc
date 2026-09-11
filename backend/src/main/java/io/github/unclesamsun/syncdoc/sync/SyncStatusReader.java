package io.github.unclesamsun.syncdoc.sync;

import io.github.unclesamsun.syncdoc.sync.domain.SyncJobEntity;
import io.github.unclesamsun.syncdoc.sync.domain.SyncRunEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 작업과 이력에서 화면이 쓸 수집 상태를 만든다. 권한 확인은 하지 않는다 —
 * 부르는 쪽이 프로젝트를 볼 수 있는지 먼저 확인한다.
 *
 * <p>프로젝트 목록(API-010)과 수집 상태(API-014)가 같은 규칙으로 상태를 읽게 하려고 따로 두었다.
 */
@Component
public class SyncStatusReader {

    private final SyncQueue queue;

    public SyncStatusReader(SyncQueue queue) {
        this.queue = queue;
    }

    /**
     * @param state       queued·running·succeeded·failed
     * @param nextRetryAt 다음 시도 예정 시각. 실패 후 기다리는 중일 때만 값이 있다
     * @param pending     진행 중이거나 예정된 수집이 있다
     */
    public record SyncStatus(String state, Instant lastAttemptAt, Instant lastSuccessAt, String errorCode,
                             Instant nextRetryAt, boolean pending) {
    }

    public SyncStatus statusOf(UUID projectId) {
        Optional<SyncJobEntity> active = queue.activeJob(projectId);
        Optional<SyncRunEntity> last = queue.lastRun(projectId);
        Optional<SyncRunEntity> success = queue.lastSuccessfulRun(projectId);

        boolean lastFailed = last.map(run -> SyncRunEntity.FAILED.equals(run.getOutcome())).orElse(false);
        boolean running = active.map(job -> SyncJobEntity.RUNNING.equals(job.getState())).orElse(false);

        // 실패는 재시도가 예약되어 있어도 실패로 보여야 한다. UI-008이 마지막 정상 데이터와
        // 실패 시각을 함께 보여주는 근거이고, 대기 중으로 덮으면 갱신이 멈춘 사실이 가려진다.
        String state;
        if (running) {
            state = SyncJobEntity.RUNNING;
        } else if (lastFailed) {
            state = SyncRunEntity.FAILED;
        } else if (active.isPresent()) {
            state = SyncJobEntity.QUEUED;
        } else {
            state = last.isPresent() ? SyncRunEntity.SUCCEEDED : SyncJobEntity.QUEUED;
        }

        Instant nextRetryAt = active
                .filter(job -> SyncJobEntity.QUEUED.equals(job.getState()) && job.getAttempt() > 0)
                .map(SyncJobEntity::getDueAt)
                .orElse(null);

        return new SyncStatus(
                state,
                last.map(SyncRunEntity::getStartedAt).orElse(null),
                success.map(SyncRunEntity::getFinishedAt).orElse(null),
                last.filter(run -> SyncRunEntity.FAILED.equals(run.getOutcome()))
                        .map(SyncRunEntity::getErrorCode).orElse(null),
                nextRetryAt,
                active.isPresent());
    }
}
