package io.github.unclesamsun.syncdoc.dashboard;

import io.github.unclesamsun.syncdoc.dashboard.domain.IssueSnapshotEntity;
import io.github.unclesamsun.syncdoc.dashboard.domain.IssueSnapshotRepository;
import io.github.unclesamsun.syncdoc.dashboard.domain.TaskEntity;
import io.github.unclesamsun.syncdoc.dashboard.domain.TaskRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 명세의 작업과 GitHub의 Issue를 잇는다.
 *
 * <p>연결은 Issue 제목의 `TASK-NNN:` 접두사로 한다(2026-09-21 사용자 확정). 한 작업 ID를 여러 Issue가
 * 주장하면 임의로 고르지 않고 충돌로 표시한다 — 아무거나 고르면 어느 쪽 상태가 보이는지 사람이 알 수 없다.
 *
 * <p>연결은 조회할 때 계산한다. 수집 때 고정해 두면 Issue 상태가 바뀌어도 옛 연결이 남는다.
 */
@Service
public class TaskMappingService {

    private final TaskRepository tasks;
    private final IssueSnapshotRepository issues;
    private final ProgressCalculator progress;
    private final ObjectMapper json;

    public TaskMappingService(TaskRepository tasks, IssueSnapshotRepository issues,
                              ProgressCalculator progress, ObjectMapper json) {
        this.tasks = tasks;
        this.issues = issues;
        this.progress = progress;
        this.json = json;
    }

    /**
     * @param issueNumber      Issue가 없으면 null. 화면은 `—`로 표시한다
     * @param mappingConflict  같은 작업 ID를 여러 Issue가 주장한다. 상태를 확정하지 않는다
     * @param pullRequests     이 작업과 이어진 PR 번호들. 완료 수를 세는 데 쓰지 않는다
     */
    public record TaskView(String taskSpecId, String title, UUID documentId, String anchor,
                           Integer issueNumber, String issueTitle, String status, List<String> assignees,
                           List<Integer> pullRequests, boolean mappingConflict, Instant observedAt) {
    }

    @Transactional(readOnly = true)
    public List<TaskView> map(UUID projectId, UUID snapshotId) {
        List<TaskEntity> specTasks = tasks.findBySnapshotIdOrderByTaskSpecId(snapshotId);
        Map<String, List<IssueSnapshotEntity>> issuesByTask = new HashMap<>();
        for (IssueSnapshotEntity issue : issues.findByProjectId(projectId)) {
            if (issue.getTaskSpecId() != null) {
                issuesByTask.computeIfAbsent(issue.getTaskSpecId(), key -> new ArrayList<>()).add(issue);
            }
        }

        List<TaskView> views = new ArrayList<>();
        for (TaskEntity task : specTasks) {
            List<IssueSnapshotEntity> claimed = issuesByTask.getOrDefault(task.getTaskSpecId(), List.of());
            boolean conflict = claimed.size() > 1;
            IssueSnapshotEntity issue = conflict || claimed.isEmpty() ? null : claimed.getFirst();

            views.add(new TaskView(
                    task.getTaskSpecId(),
                    task.getTitle(),
                    task.getDocumentId(),
                    task.getAnchor(),
                    issue == null ? null : issue.getNumber(),
                    issue == null ? null : issue.getTitle(),
                    conflict ? "mapping_conflict" : progress.statusOf(issue),
                    issue == null ? List.of() : readStrings(issue.getAssigneesJson()),
                    issue == null ? List.of() : readPullRequestNumbers(issue.getLinkedPrsJson()),
                    conflict,
                    issue == null ? null : issue.getObservedAt()));
        }
        return List.copyOf(views);
    }

    /** 명세에 없는데 Issue만 있는 작업 ID. 목록에서 빠지면 "명세에 없는 작업"이 보이지 않는다. */
    @Transactional(readOnly = true)
    public List<String> unmatchedIssueTaskIds(UUID projectId, UUID snapshotId) {
        Map<String, Boolean> known = new LinkedHashMap<>();
        tasks.findBySnapshotIdOrderByTaskSpecId(snapshotId)
                .forEach(task -> known.put(task.getTaskSpecId(), true));
        return issues.findByProjectId(projectId).stream()
                .map(IssueSnapshotEntity::getTaskSpecId)
                .filter(id -> id != null && !known.containsKey(id))
                .distinct()
                .toList();
    }

    private List<String> readStrings(String value) {
        try {
            return json.readValue(value, new tools.jackson.core.type.TypeReference<List<String>>() {
            });
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private List<Integer> readPullRequestNumbers(String value) {
        try {
            List<Map<String, Object>> pulls = json.readValue(value,
                    new tools.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                    });
            return pulls.stream()
                    .map(pull -> pull.get("number"))
                    .filter(number -> number instanceof Number)
                    .map(number -> ((Number) number).intValue())
                    .toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
