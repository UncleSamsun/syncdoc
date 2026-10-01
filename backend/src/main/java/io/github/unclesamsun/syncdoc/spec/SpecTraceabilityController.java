package io.github.unclesamsun.syncdoc.spec;

import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.common.ApiError;
import io.github.unclesamsun.syncdoc.common.ApiPaths;
import io.github.unclesamsun.syncdoc.document.DocumentService;
import io.github.unclesamsun.syncdoc.project.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
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

/** API-027/028 requirement/task relations and findings. */
@RestController
@RequestMapping(ApiPaths.BASE)
public class SpecTraceabilityController {

    private final SpecTraceabilityService traceability;

    public SpecTraceabilityController(SpecTraceabilityService traceability) {
        this.traceability = traceability;
    }

    /** API-027. */
    @GetMapping("/projects/{id}/spec-traceability")
    public ResponseEntity<SpecTraceabilityService.View> view(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID snapshotId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "all") String coverage,
            HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(traceability.view(user(request), id, snapshotId, page, size, coverage));
    }

    @GetMapping("/projects/{id}/spec-traceability/findings")
    public ResponseEntity<SpecTraceabilityService.FindingsView> findings(
            @PathVariable UUID id, @RequestParam(required = false) UUID snapshotId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
            .body(traceability.findings(user(request), id, snapshotId, page, size));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_QUERY", "조회 조건을 확인하세요.", requestId()));
    }

    private static CurrentUser user(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(CurrentUser.ATTRIBUTE);
    }

    /** 볼 수 없는 프로젝트와 없는 프로젝트를 구분하지 않는다. */
    @ExceptionHandler(ProjectService.ProjectNotFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RESOURCE_NOT_FOUND", "대상을 찾을 수 없습니다.", requestId()));
    }

    @ExceptionHandler(DocumentService.DocumentsNotReadyException.class)
    ResponseEntity<ApiError> notReady() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("DOCUMENTS_NOT_READY", "첫 수집이 끝나면 확인할 수 있습니다.", requestId()));
    }

    @ExceptionHandler(DocumentService.SnapshotGoneException.class)
    ResponseEntity<ApiError> gone() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiError.of("SNAPSHOT_GONE", "이 버전은 더 이상 제공되지 않습니다.", requestId()));
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
