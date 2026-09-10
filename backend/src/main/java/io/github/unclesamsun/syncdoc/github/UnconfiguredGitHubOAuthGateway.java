package io.github.unclesamsun.syncdoc.github;

/** 자격증명이 없을 때의 기본 구현. 어떤 설정이 없는지 말하고 실패한다. */
public class UnconfiguredGitHubOAuthGateway implements GitHubOAuthGateway {

    @Override
    public String authorizeUrl(String state, String codeChallenge) {
        throw new GitHubGatewayNotConfiguredException();
    }

    @Override
    public GitHubTokens exchangeCode(String code, String codeVerifier) {
        throw new GitHubGatewayNotConfiguredException();
    }

    @Override
    public GitHubTokens refreshTokens(String refreshToken) {
        throw new GitHubGatewayNotConfiguredException();
    }
}
