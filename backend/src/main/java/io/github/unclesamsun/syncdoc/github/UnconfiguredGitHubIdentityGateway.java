package io.github.unclesamsun.syncdoc.github;

/** 자격증명이 없을 때의 기본 구현. 조용히 통과하지 않고 어떤 설정이 없는지 말하고 실패한다. */
public class UnconfiguredGitHubIdentityGateway implements GitHubIdentityGateway {

    @Override
    public GitHubUser fetchAuthenticatedUser(String userAccessToken) {
        throw new GitHubGatewayNotConfiguredException();
    }
}
