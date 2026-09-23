package io.github.unclesamsun.syncdoc.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** 사용자 GitHub 토큰. 평문을 저장하지 않는다. 회전 중복을 낙관적 잠금으로 막는다. */
@Entity
@Table(name = "user_credentials")
public class UserCredentialEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "access_token_ciphertext", nullable = false)
    private String accessTokenCiphertext;

    @Column(name = "refresh_token_ciphertext")
    private String refreshTokenCiphertext;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "refresh_expires_at")
    private Instant refreshExpiresAt;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Version
    @Column(nullable = false)
    private long version;

    protected UserCredentialEntity() {
    }

    public UserCredentialEntity(UUID userId, String accessTokenCiphertext, String refreshTokenCiphertext,
                                Instant expiresAt, Instant refreshExpiresAt, int keyVersion) {
        this.userId = userId;
        this.accessTokenCiphertext = accessTokenCiphertext;
        this.refreshTokenCiphertext = refreshTokenCiphertext;
        this.expiresAt = expiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
        this.keyVersion = keyVersion;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAccessTokenCiphertext() {
        return accessTokenCiphertext;
    }

    public String getRefreshTokenCiphertext() {
        return refreshTokenCiphertext;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public void replaceTokens(String accessTokenCiphertext, String refreshTokenCiphertext,
                              Instant expiresAt, Instant refreshExpiresAt, int keyVersion) {
        this.accessTokenCiphertext = accessTokenCiphertext;
        this.refreshTokenCiphertext = refreshTokenCiphertext;
        this.expiresAt = expiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
        this.keyVersion = keyVersion;
    }
}
