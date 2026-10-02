package io.github.unclesamsun.syncdoc.spec;
import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.common.*;
import io.github.unclesamsun.syncdoc.document.DocumentService;
import io.github.unclesamsun.syncdoc.project.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping(ApiPaths.BASE)
public class SnapshotComparisonController {
 private final SnapshotComparisonService comparisons;
 public SnapshotComparisonController(SnapshotComparisonService comparisons){this.comparisons=comparisons;}
 private static <T> ResponseEntity<T> ok(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);}
 @GetMapping("/projects/{id}/snapshots")
 public ResponseEntity<SnapshotComparisonService.SnapshotPage> list(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,HttpServletRequest request){return ok(comparisons.list(user(request),id,page,size));}
 @GetMapping("/projects/{id}/snapshot-comparison")
 public ResponseEntity<SnapshotComparisonService.Summary> summary(@PathVariable UUID id,@RequestParam UUID fromSnapshotId,@RequestParam UUID toSnapshotId,HttpServletRequest request){return ok(comparisons.summary(user(request),id,fromSnapshotId,toSnapshotId));}
 @GetMapping("/projects/{id}/snapshot-comparison/documents")
 public ResponseEntity<SnapshotComparisonService.ResultPage<ComparisonEngine.Row>> documents(@PathVariable UUID id,@RequestParam UUID fromSnapshotId,@RequestParam UUID toSnapshotId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(defaultValue="all") String change,HttpServletRequest request){return ok(comparisons.rows(user(request),id,fromSnapshotId,toSnapshotId,"documents","all",change,page,size));}
 @GetMapping("/projects/{id}/snapshot-comparison/items")
 public ResponseEntity<SnapshotComparisonService.ResultPage<ComparisonEngine.Row>> items(@PathVariable UUID id,@RequestParam UUID fromSnapshotId,@RequestParam UUID toSnapshotId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(defaultValue="all") String change,@RequestParam(defaultValue="all") String kind,HttpServletRequest request){return ok(comparisons.rows(user(request),id,fromSnapshotId,toSnapshotId,"items",kind,change,page,size));}
 @GetMapping("/projects/{id}/snapshot-comparison/impacts")
 public ResponseEntity<SnapshotComparisonService.ResultPage<SnapshotComparisonService.ImpactView>> impacts(@PathVariable UUID id,@RequestParam UUID fromSnapshotId,@RequestParam UUID toSnapshotId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(defaultValue="all") String change,HttpServletRequest request){return ok(comparisons.impacts(user(request),id,fromSnapshotId,toSnapshotId,change,page,size));}
 @ExceptionHandler(SnapshotComparisonService.ScopeMismatchException.class)
 ResponseEntity<ApiError> mismatch(){return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of("COMPARISON_SCOPE_MISMATCH","같은 수집 브랜치와 문서 루트의 게시본을 선택하세요.",requestId()));}
 @ExceptionHandler(IllegalArgumentException.class)
 ResponseEntity<ApiError> invalid(){return ResponseEntity.badRequest().body(ApiError.of("INVALID_QUERY","비교 조건을 확인하세요.",requestId()));}
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
