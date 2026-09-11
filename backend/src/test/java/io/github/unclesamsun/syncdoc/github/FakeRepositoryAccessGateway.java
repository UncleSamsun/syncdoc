package io.github.unclesamsun.syncdoc.github;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 토큰별로 무엇이 보이는지를 테스트가 직접 등록하는 fake.
 * 토큰을 나누어 등록하면 "다른 사용자에게는 보이지 않는 저장소"를 그대로 표현할 수 있다.
 */
public class FakeRepositoryAccessGateway implements RepositoryAccessGateway {

    private final Map<String, List<GitHubRepository>> byToken = new HashMap<>();
    private final Map<String, List<GitHubBranch>> branches = new HashMap<>();
    private final Set<String> paths = new HashSet<>();
    private boolean complete = true;
    private RuntimeException failure;

    public void registerRepository(String userAccessToken, GitHubRepository repository) {
        byToken.computeIfAbsent(userAccessToken, key -> new ArrayList<>()).add(repository);
    }

    public void registerBranch(String githubRepositoryId, String name, boolean isDefault) {
        branches.computeIfAbsent(githubRepositoryId, key -> new ArrayList<>())
                .add(new GitHubBranch(name, isDefault));
    }

    public void registerPath(String githubRepositoryId, String branch, String path) {
        paths.add(key(githubRepositoryId, branch, path));
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    /** GitHub 권한을 확인할 수 없는 상황을 만든다. */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public AccessibleRepositories listAccessibleRepositories(String userAccessToken) {
        if (failure != null) {
            throw failure;
        }
        return new AccessibleRepositories(List.copyOf(byToken.getOrDefault(userAccessToken, List.of())), complete);
    }

    @Override
    public Optional<GitHubRepository> findRepository(String userAccessToken, String githubRepositoryId) {
        return listAccessibleRepositories(userAccessToken).items().stream()
                .filter(repository -> repository.githubRepositoryId().equals(githubRepositoryId))
                .findFirst();
    }

    @Override
    public List<GitHubBranch> listBranches(String userAccessToken, String githubRepositoryId) {
        findRepository(userAccessToken, githubRepositoryId)
                .orElseThrow(() -> new GitHubLookupFailedException("저장소를 볼 수 없다"));
        return List.copyOf(branches.getOrDefault(githubRepositoryId, List.of()));
    }

    @Override
    public boolean pathExists(String userAccessToken, String githubRepositoryId, String branch, String path) {
        findRepository(userAccessToken, githubRepositoryId)
                .orElseThrow(() -> new GitHubLookupFailedException("저장소를 볼 수 없다"));
        return paths.contains(key(githubRepositoryId, branch, path));
    }

    private static String key(String githubRepositoryId, String branch, String path) {
        return githubRepositoryId + "\n" + branch + "\n" + path;
    }
}
