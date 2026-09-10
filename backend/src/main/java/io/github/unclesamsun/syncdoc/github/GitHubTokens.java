package io.github.unclesamsun.syncdoc.github;

import java.time.Instant;

/** GitHub가 발급한 사용자 토큰. 로그에 값이 새지 않도록 toString을 덮는다. */
public record GitHubTokens(String accessToken, String refreshToken, Instant expiresAt, Instant refreshExpiresAt) {

    @Override
    public String toString() {
        return "GitHubTokens[보관 중]";
    }
}
