package io.github.unclesamsun.syncdoc.github;

/** GitHub OAuth 흐름. 실제 구현은 GitHub App 자격증명이 있을 때만 동작한다. */
public interface GitHubOAuthGateway {

    String authorizeUrl(String state, String codeChallenge);

    GitHubTokens exchangeCode(String code, String codeVerifier);

    /** 만료가 켜진 사용자 토큰을 갱신한다. */
    GitHubTokens refreshTokens(String refreshToken);
}
