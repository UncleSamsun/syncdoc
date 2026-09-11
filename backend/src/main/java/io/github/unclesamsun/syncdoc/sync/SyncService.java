package io.github.unclesamsun.syncdoc.sync;

import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.project.ProjectService;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * API-013·API-014. 수집 요청과 상태 조회.
 *
 * <p>두 계약 모두 프로젝트를 볼 수 있는지 먼저 확인한다. 볼 수 없는 프로젝트는 없는 것과 같은
 * 응답을 주고, 상태 값으로 프로젝트의 존재를 알리지 않는다.
 */
@Service
public class SyncService {

    private final ProjectService projects;
    private final SyncQueue queue;
    private final SyncStatusReader status;

    public SyncService(ProjectService projects, SyncQueue queue, SyncStatusReader status) {
        this.projects = projects;
        this.queue = queue;
        this.status = status;
    }

    /** API-013. 연결자 또는 관리자만 부를 수 있다. 사용자가 직접 요청했으므로 기다리던 재시도를 앞당긴다. */
    public SyncQueue.Scheduled request(CurrentUser user, UUID projectId) {
        ProjectService.ProjectView project = projects.view(user, projectId);
        if (!project.manageable()) {
            throw new ProjectService.ProjectNotFoundException();
        }
        return queue.request(projectId, true);
    }

    /** API-014. 프로젝트를 볼 수 있는 사용자면 상태를 볼 수 있다. */
    public SyncStatusReader.SyncStatus status(CurrentUser user, UUID projectId) {
        projects.view(user, projectId);
        return status.statusOf(projectId);
    }
}
