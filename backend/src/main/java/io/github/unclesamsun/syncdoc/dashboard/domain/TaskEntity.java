package io.github.unclesamsun.syncdoc.dashboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * 명세에서 뽑은 작업 하나. 정본은 저장소의 작업계획 문서이고 이 행은 그 게시본 기준 사본이다.
 *
 * <p>Issue 연결은 파생값이다. 연결이 없다고 작업이 사라지지 않는다 — 명세의 확정 작업이
 * Issue 미등록이라는 이유로 분모에서 빠지면 완료율이 실제보다 높아진다.
 */
@Entity
@Table(name = "tasks")
public class TaskEntity {

    @Id
    private UUID id;

    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private UUID snapshotId;

    @Column(name = "task_spec_id", nullable = false, updatable = false)
    private String taskSpecId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(nullable = false)
    private String anchor;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(name = "source_refs_json", nullable = false)
    private String sourceRefsJson;

    @Column(name = "validation_refs_json", nullable = false)
    private String validationRefsJson;

    @Column(name = "github_issue_node_id")
    private String githubIssueNodeId;

    @Column(name = "mapping_conflict", nullable = false)
    private boolean mappingConflict;

    protected TaskEntity() {
    }

    public TaskEntity(UUID snapshotId, String taskSpecId, UUID documentId, String anchor, String title,
                      boolean confirmed) {
        this.id = UUID.randomUUID();
        this.snapshotId = snapshotId;
        this.taskSpecId = taskSpecId;
        this.documentId = documentId;
        this.anchor = anchor;
        this.title = title;
        this.confirmed = confirmed;
        this.sourceRefsJson = "[]";
        this.validationRefsJson = "[]";
        this.mappingConflict = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public String getTaskSpecId() {
        return taskSpecId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getAnchor() {
        return anchor;
    }

    public String getTitle() {
        return title;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getGithubIssueNodeId() {
        return githubIssueNodeId;
    }

    public boolean isMappingConflict() {
        return mappingConflict;
    }
}
