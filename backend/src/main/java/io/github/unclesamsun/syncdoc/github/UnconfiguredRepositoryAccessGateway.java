package io.github.unclesamsun.syncdoc.github;

import java.util.List;
import java.util.Optional;

/** 자격증명이 없을 때의 기본 구현. 빈 목록을 돌려주지 않고 실패한다. */
public class UnconfiguredRepositoryAccessGateway implements RepositoryAccessGateway {

    @Override
    public AccessibleRepositories listAccessibleRepositories(String userAccessToken) {
        throw new GitHubGatewayNotConfiguredException();
    }

    @Override
    public Optional<GitHubRepository> findRepository(String userAccessToken, String githubRepositoryId) {
        throw new GitHubGatewayNotConfiguredException();
    }

    @Override
    public List<GitHubBranch> listBranches(String userAccessToken, String githubRepositoryId) {
        throw new GitHubGatewayNotConfiguredException();
    }

    @Override
    public boolean pathExists(String userAccessToken, String githubRepositoryId, String branch, String path) {
        throw new GitHubGatewayNotConfiguredException();
    }
}
