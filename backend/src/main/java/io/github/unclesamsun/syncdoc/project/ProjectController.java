package io.github.unclesamsun.syncdoc.project;

import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.auth.UserCredentialService;
import io.github.unclesamsun.syncdoc.common.ApiError;
import io.github.unclesamsun.syncdoc.common.ApiPaths;
import io.github.unclesamsun.syncdoc.github.GitHubBranch;
import io.github.unclesamsun.syncdoc.github.GitHubGatewayNotConfiguredException;
import io.github.unclesamsun.syncdoc.github.GitHubLookupFailedException;
import io.github.unclesamsun.syncdoc.github.GitHubRepository;
import io.github.unclesamsun.syncdoc.github.RepositoryAccessGateway;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-008·API-024 후보 조회와 API-009 ~ API-012 프로젝트. */
@RestController
@RequestMapping(ApiPaths.BASE)
public class ProjectController {

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    public record RepositoryItem(String githubRepositoryId, String fullName, boolean isPrivate,
                                 String defaultBranch) {
    }

    public record RepositoryListResponse(List<RepositoryItem> items, boolean complete) {
    }

    public record BranchListResponse(List<GitHubBranch> items) {
    }

    public record ProjectListResponse(List<ProjectService.ProjectView> items) {
    }

    /** API-008. */
    @GetMapping("/github/repositories")
    public RepositoryListResponse repositories(HttpServletRequest request) {
        RepositoryAccessGateway.AccessibleRepositories accessible = projects.accessibleRepositories(user(request));
        return new RepositoryListResponse(accessible.items().stream().map(ProjectController::toItem).toList(),
                accessible.complete());
    }

    /** API-024. */
    @GetMapping("/github/repositories/{githubRepositoryId}/branches")
    public BranchListResponse branches(@PathVariable String githubRepositoryId, HttpServletRequest request) {
        return new BranchListResponse(projects.branchesOf(user(request), githubRepositoryId));
    }

    /** API-009. */
    @PostMapping("/projects")
    public ResponseEntity<ProjectService.ProjectView> connect(
            @RequestBody ProjectService.ConnectCommand command, HttpServletRequest request) {
        if (command == null || command.githubRepositoryId() == null || command.githubRepositoryId().isBlank()) {
            throw new ProjectService.InvalidConnectionException("githubRepositoryId", "저장소를 선택하세요.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(projects.connect(user(request), command));
    }

    /** API-010. */
    @GetMapping("/projects")
    public ProjectListResponse list(HttpServletRequest request) {
        return new ProjectListResponse(projects.listVisible(user(request)));
    }

    /** API-011. */
    @GetMapping("/projects/{id}")
    public ProjectService.ProjectView view(@PathVariable UUID id, HttpServletRequest request) {
        return projects.view(user(request), id);
    }

    /** API-012. */
    @PatchMapping("/projects/{id}")
    public ProjectService.ProjectView update(@PathVariable UUID id,
                                             @RequestBody ProjectService.UpdateCommand command,
                                             HttpServletRequest request) {
        return projects.update(user(request), id, command);
    }

    private static CurrentUser user(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(CurrentUser.ATTRIBUTE);
    }

    private static RepositoryItem toItem(GitHubRepository repository) {
        return new RepositoryItem(repository.githubRepositoryId(), repository.fullName(),
                repository.isPrivate(), repository.defaultBranch());
    }

    @ExceptionHandler(ProjectService.ProjectNotFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RESOURCE_NOT_FOUND", "대상을 찾을 수 없습니다.", newRequestId()));
    }

    @ExceptionHandler(ProjectService.AlreadyConnectedException.class)
    ResponseEntity<ApiError> alreadyConnected(ProjectService.AlreadyConnectedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("REPOSITORY_ALREADY_CONNECTED", "이미 연결된 저장소입니다.",
                        newRequestId(), Map.of("projectId", e.projectId().toString())));
    }

    @ExceptionHandler({ProjectService.InvalidConnectionException.class,
            DocsRootPolicy.InvalidDocsRootException.class})
    ResponseEntity<ApiError> invalid(RuntimeException e) {
        String field = e instanceof ProjectService.InvalidConnectionException invalid
                ? invalid.field() : "docsRoot";
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("INVALID_REQUEST", e.getMessage(), newRequestId(),
                        Map.of("field", field)));
    }

    @ExceptionHandler(ProjectService.VersionConflictException.class)
    ResponseEntity<ApiError> versionConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("VERSION_CONFLICT",
                        "다른 사용자가 연결 설정을 먼저 바꿨습니다.", newRequestId()));
    }

    /**
     * GitHub 권한을 확인할 수 없는 상태다. REQ-007에 따라 허용도 거절도 아닌 별개 상태로 다루고
     * 예전에 허용됐다는 사실로 내용을 돌려주지 않는다.
     */
    @ExceptionHandler({GitHubLookupFailedException.class, GitHubGatewayNotConfiguredException.class})
    ResponseEntity<ApiError> githubUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("GITHUB_UNAVAILABLE", "GitHub 권한을 확인할 수 없습니다.", newRequestId()));
    }

    @ExceptionHandler(UserCredentialService.MissingCredentialException.class)
    ResponseEntity<ApiError> missingCredential() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("UNAUTHENTICATED", "다시 로그인해 주세요.", newRequestId()));
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString();
    }
}
