package io.github.unclesamsun.syncdoc.dashboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * GitHub에서 읽은 Issue 하나. 관찰 시각을 함께 남겨 언제 기준 정보인지 알 수 있게 한다.
 *
 * <p>상태의 정본은 GitHub다. 이 행이 오래됐다고 GitHub 상태를 바꾸지 않으며, 화면은 관찰 시각을
 * 함께 보여준다.
 */
@Entity
@Table(name = "github_issue_snapshots")
public class IssueSnapshotEntity {

    public static final String OPEN = "open";
    public static final String CLOSED = "closed";
    /** GitHub가 not planned로 닫은 Issue다. 취소이며 완료가 아니다. */
    public static final String NOT_PLANNED = "not_planned";

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "github_issue_node_id", nullable = false, updatable = false)
    private String githubIssueNodeId;

    @Column(nullable = false)
    private int number;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String state;

    @Column(name = "state_reason")
    private String stateReason;

    /** 제목 접두사에서 읽은 작업 ID. 접두사가 없으면 null이다. */
    @Column(name = "task_spec_id")
    private String taskSpecId;

    @Column(name = "assignees_json", nullable = false)
    private String assigneesJson;

    @Column(name = "labels_json", nullable = false)
    private String labelsJson;

    @Column(name = "linked_prs_json", nullable = false)
    private String linkedPrsJson;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    protected IssueSnapshotEntity() {
    }

    public IssueSnapshotEntity(UUID projectId, String githubIssueNodeId, int number, String title,
                               String state, String stateReason, String taskSpecId, String assigneesJson,
                               String labelsJson, String linkedPrsJson, Instant observedAt) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.githubIssueNodeId = githubIssueNodeId;
        this.number = number;
        this.title = title;
        this.state = state;
        this.stateReason = stateReason;
        this.taskSpecId = taskSpecId;
        this.assigneesJson = assigneesJson;
        this.labelsJson = labelsJson;
        this.linkedPrsJson = linkedPrsJson;
        this.observedAt = observedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getGithubIssueNodeId() {
        return githubIssueNodeId;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getState() {
        return state;
    }

    public String getStateReason() {
        return stateReason;
    }

    public String getTaskSpecId() {
        return taskSpecId;
    }

    public String getAssigneesJson() {
        return assigneesJson;
    }

    public String getLinkedPrsJson() {
        return linkedPrsJson;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    /** 취소로 닫혔는지. 완료와 구분해야 분모에서 빼고 완료에서도 뺄 수 있다. */
    public boolean isCanceled() {
        return CLOSED.equals(state) && NOT_PLANNED.equals(stateReason);
    }

    public boolean isCompleted() {
        return CLOSED.equals(state) && !NOT_PLANNED.equals(stateReason);
    }
}
