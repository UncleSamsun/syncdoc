package io.github.unclesamsun.syncdoc.sync;

import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.common.ApiError;
import io.github.unclesamsun.syncdoc.common.ApiPaths;
import io.github.unclesamsun.syncdoc.github.GitHubGatewayNotConfiguredException;
import io.github.unclesamsun.syncdoc.github.GitHubLookupFailedException;
import io.github.unclesamsun.syncdoc.project.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-013 수집 예약과 API-014 수집 상태. */
@RestController
@RequestMapping(ApiPaths.BASE)
public class SyncController {

    private final SyncService sync;

    public SyncController(SyncService sync) {
        this.sync = sync;
    }

    /** API-013. 같은 입력으로 다시 불러도 활성 작업이 있으면 새 작업을 만들지 않는다. */
    @PostMapping("/projects/{id}/sync")
    public ResponseEntity<SyncQueue.Scheduled> request(@PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.accepted().body(sync.request(user(request), id));
    }

    /** API-014. 토큰과 내부 경로는 담지 않는다. */
    @GetMapping("/projects/{id}/sync")
    public SyncStatusReader.SyncStatus status(@PathVariable UUID id, HttpServletRequest request) {
        return sync.status(user(request), id);
    }

    private static CurrentUser user(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(CurrentUser.ATTRIBUTE);
    }

    @ExceptionHandler(ProjectService.ProjectNotFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RESOURCE_NOT_FOUND", "대상을 찾을 수 없습니다.", UUID.randomUUID().toString()));
    }

    @ExceptionHandler({GitHubLookupFailedException.class, GitHubGatewayNotConfiguredException.class})
    ResponseEntity<ApiError> githubUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("GITHUB_UNAVAILABLE", "GitHub 권한을 확인할 수 없습니다.",
                        UUID.randomUUID().toString()));
    }
}
