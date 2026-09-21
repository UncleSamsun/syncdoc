package io.github.unclesamsun.syncdoc.dashboard;

import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.common.ApiError;
import io.github.unclesamsun.syncdoc.common.ApiPaths;
import io.github.unclesamsun.syncdoc.github.GitHubGatewayNotConfiguredException;
import io.github.unclesamsun.syncdoc.github.GitHubLookupFailedException;
import io.github.unclesamsun.syncdoc.project.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-015 현황 집계와 API-016 작업 목록. */
@RestController
@RequestMapping(ApiPaths.BASE)
public class OverviewController {

    private final OverviewService overview;
    private final TaskMappingService mapping;
    private final ProjectService projects;

    public OverviewController(OverviewService overview, TaskMappingService mapping,
                              ProjectService projects) {
        this.overview = overview;
        this.mapping = mapping;
        this.projects = projects;
    }

    public record TaskListResponse(UUID snapshotId, List<TaskMappingService.TaskView> items, int total,
                                   String nextCursor) {
    }

    /** API-015. */
    @GetMapping("/projects/{id}/overview")
    public ResponseEntity<OverviewService.OverviewView> overview(@PathVariable UUID id,
                                                                 HttpServletRequest request) {
        return noStore().body(overview.of(user(request), id));
    }

    /**
     * API-016. 현황이 싣는 20개를 잇는 전체 목록이다.
     *
     * @param status 상태로 걸러 본다. 없으면 전부 돌려준다
     */
    @GetMapping("/projects/{id}/tasks")
    public ResponseEntity<TaskListResponse> tasks(@PathVariable UUID id,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String assignee,
                                                  HttpServletRequest request) {
        ProjectService.ProjectView project = projects.view(user(request), id);
        if (project.currentSnapshotId() == null) {
            // 첫 수집 전이다. 빈 목록이 오류는 아니다.
            return noStore().body(new TaskListResponse(null, List.of(), 0, null));
        }
        List<TaskMappingService.TaskView> all = mapping.map(id, project.currentSnapshotId()).stream()
                .filter(task -> status == null || status.equals(task.status()))
                .filter(task -> assignee == null || task.assignees().contains(assignee))
                .toList();
        return noStore().body(new TaskListResponse(project.currentSnapshotId(), all, all.size(), null));
    }

    private static ResponseEntity.BodyBuilder noStore() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate());
    }

    private static CurrentUser user(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(CurrentUser.ATTRIBUTE);
    }

    @ExceptionHandler(ProjectService.ProjectNotFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RESOURCE_NOT_FOUND", "대상을 찾을 수 없습니다.", UUID.randomUUID().toString()));
    }

    /** GitHub 권한을 확인할 수 없다. 예전에 볼 수 있었다는 이유로 집계를 돌려주지 않는다. */
    @ExceptionHandler({GitHubLookupFailedException.class, GitHubGatewayNotConfiguredException.class})
    ResponseEntity<ApiError> githubUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("GITHUB_UNAVAILABLE", "GitHub 권한을 확인할 수 없습니다.",
                        UUID.randomUUID().toString()));
    }
}
