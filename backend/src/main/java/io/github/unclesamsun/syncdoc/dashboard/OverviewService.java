package io.github.unclesamsun.syncdoc.dashboard;

import io.github.unclesamsun.syncdoc.auth.CurrentUser;
import io.github.unclesamsun.syncdoc.auth.UserCredentialService;
import io.github.unclesamsun.syncdoc.document.domain.DocumentEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentRepository;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotEntity;
import io.github.unclesamsun.syncdoc.document.domain.DocumentSnapshotRepository;
import io.github.unclesamsun.syncdoc.github.ProjectBoardGateway;
import io.github.unclesamsun.syncdoc.project.ProjectService;
import io.github.unclesamsun.syncdoc.project.domain.ProjectEntity;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-015 현황 집계.
 *
 * <p>범위를 밝히지 않은 숫자를 내보내지 않는다. 어느 원천을 못 읽었는지는 `partial`에, Project를
 * 볼 수 없다는 사실은 `projectAccess`에 담고, 어느 쪽도 0으로 대체하지 않는다.
 */
@Service
public class OverviewService {

    /** 현황에 함께 싣는 작업 수. 전체 목록은 API-016이다. */
    private static final int TASK_PREVIEW = 20;
    private static final int RECENT_CHANGES = 10;

    private final ProjectService projects;
    private final ProjectRepository projectRepository;
    private final DocumentSnapshotRepository snapshots;
    private final DocumentRepository documents;
    private final TaskMappingService mapping;
    private final ProgressCalculator calculator;
    private final ProjectBoardGateway board;
    private final UserCredentialService credentials;

    public OverviewService(ProjectService projects, ProjectRepository projectRepository,
                           DocumentSnapshotRepository snapshots, DocumentRepository documents,
                           TaskMappingService mapping, ProgressCalculator calculator,
                           ProjectBoardGateway board, UserCredentialService credentials) {
        this.projects = projects;
        this.projectRepository = projectRepository;
        this.snapshots = snapshots;
        this.documents = documents;
        this.mapping = mapping;
        this.calculator = calculator;
        this.board = board;
        this.credentials = credentials;
    }

    /** `available`·`unavailable`·`not_connected` 셋이다. 연결 안 함과 권한 없음을 구분한다. */
    public static final String PROJECT_AVAILABLE = "available";
    public static final String PROJECT_UNAVAILABLE = "unavailable";
    public static final String PROJECT_NOT_CONNECTED = "not_connected";

    /**
     * @param partial        원천별 불완전 여부. 하나라도 true면 집계를 확정으로 쓰지 않는다
     * @param projectProgress Project 기반 완료율. 조회 불가·미연결이면 null이며 0으로 대체하지 않는다
     */
    public record OverviewView(UUID snapshotId, String sourceRevision, Instant repositoryObservedAt,
                               Instant projectObservedAt, String projectAccess,
                               ProgressCalculator.Counts counts, ProgressCalculator.Progress progress,
                               Map<String, Integer> projectProgress, int documentCount,
                               List<RecentChange> recentChanges, List<TaskMappingService.TaskView> tasks,
                               int taskTotal, List<String> unmatchedIssueTasks,
                               Map<String, Boolean> partial) {
    }

    /** @param change added·modified. 이전 게시본과 견줘 무엇이 달라졌는지만 말한다 */
    public record RecentChange(UUID documentId, String path, String title, String change) {
    }

    @Transactional(readOnly = true)
    public OverviewView of(CurrentUser user, UUID projectId) {
        projects.view(user, projectId);
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(ProjectService.ProjectNotFoundException::new);

        // Project는 한 번만 부른다. 접근 여부와 건수가 같은 조회에서 나와야 둘이 어긋나지 않는다.
        Map<String, Integer> projectCounts = projectCountsOf(user, project);
        String projectAccess = projectAccessOf(project, projectCounts);

        Optional<DocumentSnapshotEntity> current = Optional.ofNullable(project.getCurrentSnapshotId())
                .flatMap(snapshots::findById);
        if (current.isEmpty()) {
            // 첫 수집 전이다. 빈 집계를 0%로 보여주지 않고 게시본이 없다는 사실을 그대로 전한다.
            return new OverviewView(null, null, null, project.getIssuesObservedAt(), projectAccess,
                    calculator.count(List.of()), calculator.progress(calculator.count(List.of())),
                    projectCounts, 0, List.of(), List.of(), 0, List.of(),
                    partial(project, true, projectAccess));
        }

        DocumentSnapshotEntity snapshot = current.get();
        List<TaskMappingService.TaskView> tasks = mapping.map(projectId, snapshot.getId());
        ProgressCalculator.Counts counts =
                calculator.count(tasks.stream().map(TaskMappingService.TaskView::status).toList());

        return new OverviewView(
                snapshot.getId(),
                snapshot.getSourceRevision(),
                snapshot.getCreatedAt(),
                project.getIssuesObservedAt(),
                projectAccess,
                counts,
                calculator.progress(counts),
                projectCounts,
                (int) documents.countBySnapshotId(snapshot.getId()),
                recentChanges(project, snapshot),
                tasks.size() > TASK_PREVIEW ? tasks.subList(0, TASK_PREVIEW) : tasks,
                tasks.size(),
                mapping.unmatchedIssueTaskIds(projectId, snapshot.getId()),
                partial(project, false, projectAccess));
    }

    /**
     * 이전 게시본과 견줘 달라진 문서. 원문 해시가 다르면 고쳐진 것이고, 없던 경로면 새로 생긴 것이다.
     * 시각을 문서마다 따로 기록하지 않고도 "무엇이 바뀌었나"를 말할 수 있다.
     */
    private List<RecentChange> recentChanges(ProjectEntity project, DocumentSnapshotEntity snapshot) {
        List<DocumentSnapshotEntity> history = snapshots.findByProjectIdOrderByCreatedAtDesc(project.getId())
                .stream()
                .filter(DocumentSnapshotEntity::isComplete)
                .toList();
        Optional<DocumentSnapshotEntity> previous = history.stream()
                .filter(candidate -> !candidate.getId().equals(snapshot.getId()))
                .findFirst();
        if (previous.isEmpty()) {
            return List.of();
        }

        Map<String, String> before = new HashMap<>();
        documents.findBySnapshotIdOrderByPath(previous.get().getId())
                .forEach(document -> before.put(document.getPath(), document.getSourceHash()));

        List<RecentChange> changes = new ArrayList<>();
        for (DocumentEntity document : documents.findBySnapshotIdOrderByPath(snapshot.getId())) {
            String hash = before.get(document.getPath());
            if (hash == null) {
                changes.add(new RecentChange(document.getId(), document.getPath(), document.getTitle(),
                        "added"));
            } else if (!hash.equals(document.getSourceHash())) {
                changes.add(new RecentChange(document.getId(), document.getPath(), document.getTitle(),
                        "modified"));
            }
            if (changes.size() >= RECENT_CHANGES) {
                break;
            }
        }
        return List.copyOf(changes);
    }

    private static String projectAccessOf(ProjectEntity project, Map<String, Integer> counts) {
        if (project.getGithubProjectNodeId() == null) {
            // 연결하지 않은 것과 볼 수 없는 것은 사용자가 할 일이 다르다.
            return PROJECT_NOT_CONNECTED;
        }
        return counts == null ? PROJECT_UNAVAILABLE : PROJECT_AVAILABLE;
    }

    /** 요청자의 자격증명으로만 조회한다. 다른 사용자의 조회 결과를 재사용하지 않는다. */
    private Map<String, Integer> projectCountsOf(CurrentUser user, ProjectEntity project) {
        if (project.getGithubProjectNodeId() == null) {
            return null;
        }
        try {
            return board.countByStatus(credentials.accessTokenFor(user.id()),
                            project.getGithubProjectNodeId())
                    .map(ProjectBoardGateway.BoardCounts::statusCounts)
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 어느 원천을 다 읽지 못했는지. 하나라도 참이면 화면은 숫자를 확정된 값으로 보여주지 않는다.
     * 연결하지 않은 Project는 불완전이 아니다 — 읽을 것이 없는 것과 못 읽은 것은 다르다.
     */
    private static Map<String, Boolean> partial(ProjectEntity project, boolean noSnapshot,
                                                String projectAccess) {
        return Map.of(
                "documents", noSnapshot,
                "issues", project.getIssuesObservedAt() == null || !project.isIssuesComplete(),
                "project", PROJECT_UNAVAILABLE.equals(projectAccess));
    }
}
