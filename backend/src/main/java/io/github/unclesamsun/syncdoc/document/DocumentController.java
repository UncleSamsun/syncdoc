package io.github.unclesamsun.syncdoc.document;

import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.common.ApiError;
import io.github.unclesamsun.syncdoc.common.ApiPaths;
import io.github.unclesamsun.syncdoc.github.GitHubGatewayNotConfiguredException;
import io.github.unclesamsun.syncdoc.github.GitHubLookupFailedException;
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

/** API-017 문서 목록과 API-018 문서 본문. */
@RestController
@RequestMapping(ApiPaths.BASE)
public class DocumentController {

    private final DocumentService documents;

    public DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    /** API-017. */
    @GetMapping("/projects/{id}/documents")
    public ResponseEntity<DocumentService.DocumentListView> list(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID snapshotId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        return noStore().body(documents.list(user(request), id, snapshotId, cursor, limit));
    }

    /** API-018. 본문은 저장하지 않는 응답이다. */
    @GetMapping("/projects/{id}/documents/{documentId}")
    public ResponseEntity<DocumentService.DocumentView> view(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @RequestParam(required = false) UUID snapshotId,
            HttpServletRequest request) {
        return noStore().body(documents.view(user(request), id, documentId, snapshotId));
    }

    /** 계약 `## 공통`이 정한 대로 본문과 목록을 저장하지 않게 한다. */
    private static ResponseEntity.BodyBuilder noStore() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate());
    }

    private static CurrentUser user(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(CurrentUser.ATTRIBUTE);
    }

    @ExceptionHandler({ProjectService.ProjectNotFoundException.class,
            DocumentService.DocumentNotFoundException.class})
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RESOURCE_NOT_FOUND", "대상을 찾을 수 없습니다.", requestId()));
    }

    @ExceptionHandler(DocumentService.DocumentsNotReadyException.class)
    ResponseEntity<ApiError> notReady() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("DOCUMENTS_NOT_READY", "첫 수집이 끝나면 문서를 볼 수 있습니다.", requestId()));
    }

    @ExceptionHandler(DocumentService.SnapshotGoneException.class)
    ResponseEntity<ApiError> gone() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiError.of("SNAPSHOT_GONE", "이 버전은 더 이상 제공되지 않습니다.", requestId()));
    }

    @ExceptionHandler(DocumentService.DocumentNotRenderableException.class)
    ResponseEntity<ApiError> notRenderable() {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of("DOCUMENT_NOT_RENDERABLE", "이 문서를 표시할 수 없습니다.", requestId()));
    }

    @ExceptionHandler(DocumentService.InvalidCursorException.class)
    ResponseEntity<ApiError> invalidCursor() {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of("INVALID_REQUEST", "목록을 이어서 읽을 수 없습니다.", requestId()));
    }

    /** GitHub 권한을 확인할 수 없는 상태다. 예전 허용을 근거로 내용을 돌려주지 않는다. */
    @ExceptionHandler({GitHubLookupFailedException.class, GitHubGatewayNotConfiguredException.class})
    ResponseEntity<ApiError> githubUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("GITHUB_UNAVAILABLE", "GitHub 권한을 확인할 수 없습니다.", requestId()));
    }

    private static String requestId() {
        return UUID.randomUUID().toString();
    }
}
