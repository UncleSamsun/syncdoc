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
public class TaskContextController {
    private final TaskContextService contexts;
    public TaskContextController(TaskContextService contexts){this.contexts=contexts;}
    @GetMapping("/projects/{id}/tasks/{taskId}/context")
    public ResponseEntity<?> context(@PathVariable UUID id,@PathVariable String taskId,@RequestParam(required=false) UUID snapshotId,@RequestParam(defaultValue="json") String format,HttpServletRequest request){
        var view=contexts.view((CurrentUser)request.getAttribute(CurrentUser.ATTRIBUTE),id,taskId,snapshotId,format);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).header("X-Content-Type-Options","nosniff").contentType(format.equals("markdown")?MediaType.parseMediaType("text/markdown;charset=UTF-8"):MediaType.APPLICATION_JSON).body(format.equals("markdown")?view.markdown():view);
    }
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ApiError> invalid(){return error(HttpStatus.BAD_REQUEST,"INVALID_QUERY","작업 ID와 형식을 확인하세요.");}
    @ExceptionHandler({ProjectService.ProjectNotFoundException.class,TaskContextService.TaskMissingException.class}) ResponseEntity<ApiError> missing(){return error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","대상을 찾을 수 없습니다.");}
    @ExceptionHandler(TaskContextService.AmbiguousTaskException.class) ResponseEntity<ApiError> ambiguous(){return error(HttpStatus.CONFLICT,"AMBIGUOUS_TASK","작업 정의가 중복되어 선택할 수 없습니다.");}
    @ExceptionHandler(DocumentService.DocumentsNotReadyException.class) ResponseEntity<ApiError> waiting(){return error(HttpStatus.CONFLICT,"DOCUMENTS_NOT_READY","첫 수집을 기다리세요.");}
    @ExceptionHandler(DocumentService.SnapshotGoneException.class) ResponseEntity<ApiError> gone(){return error(HttpStatus.GONE,"SNAPSHOT_GONE","이 게시본은 제공되지 않습니다.");}
    private static ResponseEntity<ApiError> error(HttpStatus status,String code,String message){return ResponseEntity.status(status).body(ApiError.of(code,message,UUID.randomUUID().toString()));}
}
