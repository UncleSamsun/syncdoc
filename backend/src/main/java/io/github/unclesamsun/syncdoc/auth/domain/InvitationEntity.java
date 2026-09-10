package io.github.unclesamsun.syncdoc.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 서비스 이용 허용 목록. 재초대는 새 행을 만들지 않고 이 행을 되살린다. */
@Entity
@Table(name = "invitations")
public class InvitationEntity {

    @Id
    private UUID id;

    @Column(name = "github_user_id", nullable = false, updatable = false)
    private String githubUserId;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected InvitationEntity() {
    }

    public InvitationEntity(UUID id, String githubUserId, UUID grantedBy, Instant grantedAt) {
        this.id = id;
        this.githubUserId = githubUserId;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getGithubUserId() {
        return githubUserId;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    public void regrant(UUID grantedBy, Instant now) {
        this.grantedBy = grantedBy;
        this.grantedAt = now;
        this.revokedAt = null;
    }
}
