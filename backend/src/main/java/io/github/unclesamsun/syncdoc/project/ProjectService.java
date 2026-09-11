package io.github.unclesamsun.syncdoc.project;

import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.auth.UserCredentialService;
import io.github.unclesamsun.syncdoc.github.GitHubBranch;
import io.github.unclesamsun.syncdoc.github.GitHubRepository;
import io.github.unclesamsun.syncdoc.github.RepositoryAccessGateway;
import io.github.unclesamsun.syncdoc.project.domain.InstallationEntity;
import io.github.unclesamsun.syncdoc.project.domain.InstallationRepository;
import io.github.unclesamsun.syncdoc.project.domain.ProjectEntity;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 저장소 연결과 조회.
 *
 * <p>REQ-007에 따라 보호된 요청마다 사용자 자격증명으로 GitHub 접근을 확인하고 그 결과를
 * **요청 하나 안에서만** 재사용한다. 필드나 정적 변수에 담지 않는다. 권한이 철회되면 다음 요청에서 바로 막힌다.
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final InstallationRepository installations;
    private final RepositoryAccessGateway repositories;
    private final UserCredentialService credentials;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, InstallationRepository installations,
                          RepositoryAccessGateway repositories, UserCredentialService credentials,
                          TransactionTemplate transactions, Clock clock) {
        this.projects = projects;
        this.installations = installations;
        this.repositories = repositories;
        this.credentials = credentials;
        this.transactions = transactions;
        this.clock = clock;
    }

    public record ProjectView(UUID id, String githubRepositoryId, String fullName, String branch,
                              String docsRoot, String githubProjectNodeId, UUID currentSnapshotId,
                              String syncState, long version, boolean manageable) {
    }

    public record ConnectCommand(String githubRepositoryId, String branch, String docsRoot,
                                 String githubProjectNodeId) {
    }

    public record UpdateCommand(String branch, String docsRoot, String githubProjectNodeId,
                                Long expectedVersion) {
    }

    /** API-008. */
    public RepositoryAccessGateway.AccessibleRepositories accessibleRepositories(CurrentUser user) {
        return repositories.listAccessibleRepositories(credentials.accessTokenFor(user.id()));
    }

    /** API-024. 볼 수 없는 저장소는 없는 것과 같다. */
    public List<GitHubBranch> branchesOf(CurrentUser user, String githubRepositoryId) {
        String token = credentials.accessTokenFor(user.id());
        repositories.findRepository(token, githubRepositoryId).orElseThrow(ProjectNotFoundException::new);
        return repositories.listBranches(token, githubRepositoryId);
    }

    /**
     * API-009.
     *
     * <p>쓰기만 트랜잭션으로 감싼다. unique 제약 위반이 나면 그 트랜잭션은 롤백된 상태라
     * 같은 트랜잭션 안에서는 기존 프로젝트를 조회할 수 없다. 트랜잭션 밖에서 다시 읽어 409로 바꾼다.
     */
    public ProjectView connect(CurrentUser user, ConnectCommand command) {
        // GitHub를 부르기 전에 형식부터 거른다.
        String docsRoot = DocsRootPolicy.normalize(command.docsRoot());
        String token = credentials.accessTokenFor(user.id());

        GitHubRepository repository = repositories.findRepository(token, command.githubRepositoryId())
                .orElseThrow(ProjectNotFoundException::new);

        String branch = command.branch() == null || command.branch().isBlank()
                ? repository.defaultBranch() : command.branch().trim();
        boolean branchExists = repositories.listBranches(token, repository.githubRepositoryId()).stream()
                .anyMatch(candidate -> candidate.name().equals(branch));
        if (!branchExists) {
            throw new InvalidConnectionException("branch", "이 저장소에 " + branch + " 브랜치가 없습니다.");
        }
        if (!repositories.pathExists(token, repository.githubRepositoryId(), branch, docsRoot)) {
            throw new InvalidConnectionException("docsRoot",
                    "이 브랜치에 " + docsRoot + " 경로가 없습니다. 실제 경로를 선택하세요.");
        }

        projects.findByGithubRepositoryId(repository.githubRepositoryId())
                .ifPresent(existing -> {
                    throw new AlreadyConnectedException(existing.getId());
                });

        String finalBranch = branch;
        try {
            ProjectEntity saved = transactions.execute(status -> {
                Instant now = clock.instant();
                UUID installationId = upsertInstallation(repository, now);
                return projects.saveAndFlush(new ProjectEntity(UUID.randomUUID(),
                        repository.githubRepositoryId(), repository.fullName(), installationId,
                        user.id(), finalBranch, docsRoot, blankToNull(command.githubProjectNodeId()), now));
            });
            return toView(saved, user, true);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청이 먼저 만들었다. 미리 확인하는 것만으로는 경쟁 상태를 막지 못한다.
            UUID existingId = projects.findByGithubRepositoryId(repository.githubRepositoryId())
                    .map(ProjectEntity::getId)
                    .orElseThrow(() -> e);
            throw new AlreadyConnectedException(existingId);
        }
    }

    /** API-010. 요청자가 GitHub에서 볼 수 있는 프로젝트만 돌려준다. */
    @Transactional(readOnly = true)
    public List<ProjectView> listVisible(CurrentUser user) {
        Map<String, GitHubRepository> visible = visibleRepositories(user);
        return projects.findAllByOrderByCreatedAtDesc().stream()
                .filter(project -> visible.containsKey(project.getGithubRepositoryId()))
                .map(project -> toView(project, user, isManageable(project, user)))
                .toList();
    }

    /** API-011. 볼 수 없는 프로젝트와 없는 프로젝트는 같은 응답이다. */
    @Transactional(readOnly = true)
    public ProjectView view(CurrentUser user, UUID projectId) {
        ProjectEntity project = projects.findById(projectId).orElseThrow(ProjectNotFoundException::new);
        String token = credentials.accessTokenFor(user.id());
        repositories.findRepository(token, project.getGithubRepositoryId())
                .orElseThrow(ProjectNotFoundException::new);
        return toView(project, user, isManageable(project, user));
    }

    /** API-012. 연결자 또는 서비스 관리자만 바꿀 수 있고, 그 외에는 존재를 알리지 않는다. */
    @Transactional
    public ProjectView update(CurrentUser user, UUID projectId, UpdateCommand command) {
        ProjectEntity project = projects.findById(projectId).orElseThrow(ProjectNotFoundException::new);
        String token = credentials.accessTokenFor(user.id());
        GitHubRepository repository = repositories.findRepository(token, project.getGithubRepositoryId())
                .orElseThrow(ProjectNotFoundException::new);
        if (!isManageable(project, user)) {
            throw new ProjectNotFoundException();
        }
        if (command.expectedVersion() == null || command.expectedVersion() != project.getVersion()) {
            throw new VersionConflictException();
        }

        String branch = command.branch() == null || command.branch().isBlank()
                ? project.getBranch() : command.branch().trim();
        String docsRoot = command.docsRoot() == null || command.docsRoot().isBlank()
                ? project.getDocsRoot() : DocsRootPolicy.normalize(command.docsRoot());

        boolean branchExists = repositories.listBranches(token, project.getGithubRepositoryId()).stream()
                .anyMatch(candidate -> candidate.name().equals(branch));
        if (!branchExists) {
            throw new InvalidConnectionException("branch", "이 저장소에 " + branch + " 브랜치가 없습니다.");
        }
        if (!repositories.pathExists(token, project.getGithubRepositoryId(), branch, docsRoot)) {
            throw new InvalidConnectionException("docsRoot",
                    "이 브랜치에 " + docsRoot + " 경로가 없습니다. 실제 경로를 선택하세요.");
        }

        project.renameTo(repository.fullName());
        project.reconfigure(branch, docsRoot,
                command.githubProjectNodeId() == null
                        ? project.getGithubProjectNodeId() : blankToNull(command.githubProjectNodeId()));
        try {
            projects.saveAndFlush(project);
        } catch (OptimisticLockingFailureException e) {
            throw new VersionConflictException();
        }
        return toView(project, user, true);
    }

    private Map<String, GitHubRepository> visibleRepositories(CurrentUser user) {
        Map<String, GitHubRepository> visible = new HashMap<>();
        repositories.listAccessibleRepositories(credentials.accessTokenFor(user.id())).items()
                .forEach(repository -> visible.put(repository.githubRepositoryId(), repository));
        return visible;
    }

    private UUID upsertInstallation(GitHubRepository repository, Instant now) {
        Optional<InstallationEntity> existing =
                installations.findByGithubInstallationId(repository.installationId());
        if (existing.isPresent()) {
            existing.get().touch(ownerOf(repository), "active", now);
            return existing.get().getId();
        }
        return installations.save(new InstallationEntity(UUID.randomUUID(), repository.installationId(),
                ownerOf(repository), "active", now)).getId();
    }

    private static String ownerOf(GitHubRepository repository) {
        int slash = repository.fullName().indexOf('/');
        return slash > 0 ? repository.fullName().substring(0, slash) : repository.fullName();
    }

    private boolean isManageable(ProjectEntity project, CurrentUser user) {
        return project.getCreatedBy().equals(user.id()) || user.serviceAdmin();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ProjectView toView(ProjectEntity project, CurrentUser user, boolean manageable) {
        return new ProjectView(project.getId(), project.getGithubRepositoryId(), project.getFullName(),
                project.getBranch(), project.getDocsRoot(), project.getGithubProjectNodeId(),
                project.getCurrentSnapshotId(),
                project.getCurrentSnapshotId() == null ? "queued" : "ready",
                project.getVersion(), manageable);
    }

    /** 없는 프로젝트와 볼 수 없는 프로젝트가 같은 응답이 되게 하는 예외다. */
    public static class ProjectNotFoundException extends RuntimeException {
    }

    public static class AlreadyConnectedException extends RuntimeException {

        private final UUID projectId;

        public AlreadyConnectedException(UUID projectId) {
            this.projectId = projectId;
        }

        public UUID projectId() {
            return projectId;
        }
    }

    public static class InvalidConnectionException extends RuntimeException {

        private final String field;

        public InvalidConnectionException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    public static class VersionConflictException extends RuntimeException {
    }
}
