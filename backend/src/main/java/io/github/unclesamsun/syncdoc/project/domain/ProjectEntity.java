package io.github.unclesamsun.syncdoc.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** 연결한 저장소 하나. 용어 정의상 저장소 하나가 프로젝트 하나다. */
@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    private UUID id;

    @Column(name = "github_repository_id", nullable = false, updatable = false)
    private String githubRepositoryId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "installation_id", nullable = false)
    private UUID installationId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private String branch;

    @Column(name = "docs_root", nullable = false)
    private String docsRoot;

    @Column(name = "github_project_node_id")
    private String githubProjectNodeId;

    @Column(name = "current_snapshot_id")
    private UUID currentSnapshotId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProjectEntity() {
    }

    public ProjectEntity(UUID id, String githubRepositoryId, String fullName, UUID installationId,
                         UUID createdBy, String branch, String docsRoot, String githubProjectNodeId,
                         Instant createdAt) {
        this.id = id;
        this.githubRepositoryId = githubRepositoryId;
        this.fullName = fullName;
        this.installationId = installationId;
        this.createdBy = createdBy;
        this.branch = branch;
        this.docsRoot = docsRoot;
        this.githubProjectNodeId = githubProjectNodeId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getGithubRepositoryId() {
        return githubRepositoryId;
    }

    public String getFullName() {
        return fullName;
    }

    public UUID getInstallationId() {
        return installationId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public String getBranch() {
        return branch;
    }

    public String getDocsRoot() {
        return docsRoot;
    }

    public String getGithubProjectNodeId() {
        return githubProjectNodeId;
    }

    public UUID getCurrentSnapshotId() {
        return currentSnapshotId;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** 연결 설정을 바꾸는 유일한 지점. 낙관적 잠금이 동시 수정을 막는다. */
    public void reconfigure(String branch, String docsRoot, String githubProjectNodeId) {
        this.branch = branch;
        this.docsRoot = docsRoot;
        this.githubProjectNodeId = githubProjectNodeId;
    }

    /** 저장소 이름이 바뀌어도 프로젝트는 같다. 표시용 값만 갱신한다. */
    public void renameTo(String fullName) {
        this.fullName = fullName;
    }
}
