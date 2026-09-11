package io.github.unclesamsun.syncdoc.auth;

import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserCredentialRepository;
import io.github.unclesamsun.syncdoc.crypto.TokenCipher;
import io.github.unclesamsun.syncdoc.github.GitHubOAuthGateway;
import io.github.unclesamsun.syncdoc.github.GitHubTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GitHub를 부를 때 쓰는 사용자 access token을 공급한다.
 *
 * <p>등록한 앱이 토큰 만료를 켜 두었으므로 GitHub가 8시간짜리 토큰을 준다. 세션 수명이 그보다 길어
 * 세션은 살아 있는데 토큰이 죽는 구간이 생긴다. 만료 직전이면 여기서 갱신해 그 구간을 없앤다.
 */
@Service
public class UserCredentialService {

    /** 요청을 처리하는 동안 만료되지 않도록 미리 갱신하는 여유다. */
    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final UserCredentialRepository credentials;
    private final TokenCipher cipher;
    private final GitHubOAuthGateway oauth;
    private final Clock clock;

    public UserCredentialService(UserCredentialRepository credentials, TokenCipher cipher,
                                 GitHubOAuthGateway oauth, Clock clock) {
        this.credentials = credentials;
        this.cipher = cipher;
        this.oauth = oauth;
        this.clock = clock;
    }

    @Transactional
    public String accessTokenFor(UUID userId) {
        UserCredentialEntity credential = credentials.findById(userId)
                .orElseThrow(MissingCredentialException::new);
        if (!needsRefresh(credential)) {
            return cipher.decrypt(credential.getAccessTokenCiphertext());
        }
        GitHubTokens refreshed = oauth.refreshTokens(cipher.decrypt(credential.getRefreshTokenCiphertext()));
        credential.replaceTokens(
                cipher.encrypt(refreshed.accessToken()),
                refreshed.refreshToken() == null ? null : cipher.encrypt(refreshed.refreshToken()),
                refreshed.expiresAt(), refreshed.refreshExpiresAt(), cipher.keyVersion());
        credentials.save(credential);
        return refreshed.accessToken();
    }

    private boolean needsRefresh(UserCredentialEntity credential) {
        Instant expiresAt = credential.getExpiresAt();
        // 만료가 없는 앱 설정이면 갱신할 이유가 없고, 갱신 토큰이 없으면 갱신할 수단이 없다.
        if (expiresAt == null || credential.getRefreshTokenCiphertext() == null) {
            return false;
        }
        return !expiresAt.minus(REFRESH_MARGIN).isAfter(clock.instant());
    }

    /** 로그인은 했는데 토큰이 없다. 초대가 취소되며 지워졌거나 이 기능 이전에 만든 세션이다. */
    public static class MissingCredentialException extends RuntimeException {
    }
}
