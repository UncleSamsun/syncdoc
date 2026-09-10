package io.github.unclesamsun.syncdoc.auth;

import io.github.unclesamsun.syncdoc.common.ApiError;
import io.github.unclesamsun.syncdoc.common.ApiPaths;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-005 목록, API-006 등록, API-007 취소. 관리자만 부를 수 있다. 이메일은 보내지 않는다. */
@RestController
@RequestMapping(ApiPaths.BASE + "/invitations")
public class InvitationController {

    private static final int MAX_LIMIT = 100;

    private final InvitationService invitations;

    public InvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    public record InviteRequest(String githubLogin) {
    }

    public record ListResponse(List<InvitationService.InvitationView> items) {
    }

    /** API-005. */
    @GetMapping
    public ListResponse list(@RequestParam(defaultValue = "20") int limit) {
        return new ListResponse(invitations.list(Math.min(Math.max(limit, 1), MAX_LIMIT)));
    }

    /** API-006. */
    @PostMapping
    public ResponseEntity<InvitationService.InvitationView> invite(@RequestBody InviteRequest request,
                                                                   HttpServletRequest servletRequest) {
        if (request == null || request.githubLogin() == null || request.githubLogin().isBlank()) {
            throw new IllegalArgumentException("githubLogin이 필요하다.");
        }
        CurrentUser actor = (CurrentUser) servletRequest.getAttribute(CurrentUser.ATTRIBUTE);
        InvitationService.InviteResult result = invitations.invite(request.githubLogin().trim(), actor);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.invitation());
    }

    /** API-007. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        invitations.revoke(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvitationService.InvitationNotFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RESOURCE_NOT_FOUND", "대상을 찾을 수 없습니다.", newRequestId()));
    }

    @ExceptionHandler(InvitationService.FirstAdminNotRevocableException.class)
    ResponseEntity<ApiError> firstAdmin() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("FIRST_ADMIN_NOT_REVOCABLE", "최초 관리자는 취소할 수 없습니다.", newRequestId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid() {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiError.of("INVALID_REQUEST", "요청 값을 확인해 주세요.", newRequestId()));
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString();
    }
}
