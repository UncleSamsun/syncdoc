package io.github.unclesamsun.syncdoc.github;

import java.util.HashMap;
import java.util.Map;

/** 토큰·계정명→사용자 매핑을 테스트가 직접 채우는 fake. GitHub에 나가지 않는다. */
public class FakeGitHubIdentityGateway implements GitHubIdentityGateway {

    private final Map<String, GitHubUser> byToken = new HashMap<>();
    private final Map<String, GitHubUser> byLogin = new HashMap<>();

    public void register(String userAccessToken, GitHubUser user) {
        byToken.put(userAccessToken, user);
    }

    public void registerLogin(String login, GitHubUser user) {
        byLogin.put(login, user);
    }

    @Override
    public GitHubUser fetchAuthenticatedUser(String userAccessToken) {
        GitHubUser user = byToken.get(userAccessToken);
        if (user == null) {
            throw new IllegalArgumentException("fake에 등록되지 않은 토큰");
        }
        return user;
    }

    @Override
    public GitHubUser fetchUserByLogin(String login) {
        GitHubUser user = byLogin.get(login);
        if (user == null) {
            throw new IllegalArgumentException("fake에 등록되지 않은 login");
        }
        return user;
    }
}
