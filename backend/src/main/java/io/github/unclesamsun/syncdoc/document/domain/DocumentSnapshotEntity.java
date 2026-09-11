package io.github.unclesamsun.syncdoc.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 한 revision에서 만든 게시본. 문서를 모두 담은 뒤에야 {@code complete}가 true가 되고,
 * 그 다음에 프로젝트의 현재 snapshot으로 바뀐다. 미완성 snapshot은 어떤 화면에도 쓰이지 않는다.
 *
 * <p>renderer/policy 버전이 식별자에 들어간다. 원문이 같아도 변환 규칙이 바뀌면 다른 게시본이다.
 */
@Entity
@Table(name = "document_snapshots")
public class DocumentSnapshotEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "source_revision", nullable = false, updatable = false)
    private String sourceRevision;

    @Column(name = "renderer_version", nullable = false, updatable = false)
    private String rendererVersion;

    @Column(name = "policy_version", nullable = false, updatable = false)
    private String policyVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean complete;

    protected DocumentSnapshotEntity() {
    }

    public DocumentSnapshotEntity(UUID projectId, String sourceRevision, String rendererVersion,
                                  String policyVersion, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.sourceRevision = sourceRevision;
        this.rendererVersion = rendererVersion;
        this.policyVersion = policyVersion;
        this.createdAt = createdAt;
        this.complete = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getSourceRevision() {
        return sourceRevision;
    }

    public String getRendererVersion() {
        return rendererVersion;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isComplete() {
        return complete;
    }

    /** 문서를 모두 저장한 뒤에만 부른다. 부분 수집을 완료로 바꾸지 않는다. */
    public void markComplete() {
        this.complete = true;
    }
}
