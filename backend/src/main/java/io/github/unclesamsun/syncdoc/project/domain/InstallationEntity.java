package io.github.unclesamsun.syncdoc.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 이 서비스가 아는 GitHub App 설치. 저장소를 볼 수 있게 해 준 통로다. */
@Entity
@Table(name = "github_installations")
public class InstallationEntity {

    @Id
    private UUID id;

    @Column(name = "github_installation_id", nullable = false, updatable = false)
    private String githubInstallationId;

    @Column(name = "owner_github_id", nullable = false)
    private String ownerGithubId;

    @Column(nullable = false)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InstallationEntity() {
    }

    public InstallationEntity(UUID id, String githubInstallationId, String ownerGithubId,
                              String status, Instant updatedAt) {
        this.id = id;
        this.githubInstallationId = githubInstallationId;
        this.ownerGithubId = ownerGithubId;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getGithubInstallationId() {
        return githubInstallationId;
    }

    public void touch(String ownerGithubId, String status, Instant now) {
        this.ownerGithubId = ownerGithubId;
        this.status = status;
        this.updatedAt = now;
    }
}
