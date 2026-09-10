package io.github.unclesamsun.syncdoc.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 서비스가 아는 사용자. 동일성은 github_user_id로 판단하고 login은 표시용이다. */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(name = "github_user_id", nullable = false, updatable = false)
    private String githubUserId;

    @Column(nullable = false)
    private String login;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }

    public UserEntity(UUID id, String githubUserId, String login, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.githubUserId = githubUserId;
        this.login = login;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getGithubUserId() {
        return githubUserId;
    }

    public String getLogin() {
        return login;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** 계정명이 바뀌어도 같은 사용자다. 표시용 값만 갱신한다. */
    public void renameTo(String login, Instant now) {
        this.login = login;
        this.updatedAt = now;
    }
}
