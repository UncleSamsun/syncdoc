package io.github.unclesamsun.syncdoc.github;

import java.util.HashMap;
import java.util.Map;

/** 토큰→사용자 매핑을 테스트가 직접 채우는 fake. GitHub에 나가지 않는다. */
public class FakeGitHubIdentityGateway implements GitHubIdentityGateway {

    private final Map<String, GitHubUser> users = new HashMap<>();

    public void register(String userAccessToken, GitHubUser user) {
        users.put(userAccessToken, user);
    }

    @Override
    public GitHubUser fetchAuthenticatedUser(String userAccessToken) {
        GitHubUser user = users.get(userAccessToken);
        if (user == null) {
            throw new IllegalArgumentException("fake에 등록되지 않은 토큰");
        }
        return user;
    }
}
