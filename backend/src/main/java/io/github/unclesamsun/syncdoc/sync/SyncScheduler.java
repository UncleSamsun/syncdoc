package io.github.unclesamsun.syncdoc.sync;

import io.github.unclesamsun.syncdoc.project.domain.ProjectEntity;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import io.github.unclesamsun.syncdoc.sync.domain.SyncRunEntity;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 작업을 실제로 돌리는 쪽. API만 띄우는 프로세스에서는 {@code syncdoc.sync.worker-enabled=false}로 끈다.
 *
 * <p>주기 조회를 두는 이유는 webhook이 전부가 아니기 때문이다. 사내망이라 이벤트를 받지 못하거나
 * delivery가 유실돼도 저장소 변경을 따라가야 한다. 간격은 REQ-006이 제안한 60초다.
 */
@Component
@ConditionalOnProperty(prefix = "syncdoc.sync", name = "worker-enabled", havingValue = "true",
        matchIfMissing = true)
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    /** 한 번 깨어날 때 처리할 작업 수. 한 프로세스가 큐를 독점하지 않게 끊어 준다. */
    private static final int JOBS_PER_TICK = 5;

    private final SyncWorker worker;
    private final SyncQueue queue;
    private final ProjectRepository projects;
    private final SyncProperties properties;
    private final Clock clock;

    public SyncScheduler(SyncWorker worker, SyncQueue queue, ProjectRepository projects,
                         SyncProperties properties, Clock clock) {
        this.worker = worker;
        this.queue = queue;
        this.projects = projects;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${syncdoc.sync.worker-interval:PT1S}")
    public void drain() {
        for (int i = 0; i < JOBS_PER_TICK; i++) {
            try {
                if (!worker.runOnce()) {
                    return;
                }
            } catch (RuntimeException e) {
                // 한 작업의 실패가 다음 작업까지 막지 않게 한다.
                log.warn("작업 처리 중 예상하지 못한 오류", e);
                return;
            }
        }
    }

    @Scheduled(fixedDelayString = "${syncdoc.sync.poll-interval:PT60S}",
            initialDelayString = "${syncdoc.sync.poll-interval:PT60S}")
    public void enqueuePeriodicChecks() {
        Instant now = clock.instant();
        for (ProjectEntity project : projects.findAll()) {
            if (queue.activeJob(project.getId()).isPresent()) {
                continue;
            }
            if (dueForCheck(project, now)) {
                queue.request(project.getId(), false);
            }
        }
    }

    /** 마지막 시도가 조회 간격보다 오래됐을 때만 새 작업을 만든다. 실패 직후를 여기서 재촉하지 않는다. */
    private boolean dueForCheck(ProjectEntity project, Instant now) {
        return queue.lastRun(project.getId())
                .map(SyncRunEntity::getStartedAt)
                .map(startedAt -> startedAt.isBefore(now.minus(properties.pollInterval())))
                .orElse(true);
    }
}
