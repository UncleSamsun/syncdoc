package io.github.unclesamsun.syncdoc.github;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** code→토큰 매핑을 테스트가 채우는 fake. GitHub에 나가지 않는다. */
public class FakeGitHubOAuthGateway implements GitHubOAuthGateway {

    private final Map<String, GitHubTokens> codes = new HashMap<>();

    public void register(String code, GitHubTokens tokens) {
        codes.put(code, tokens);
    }

    public GitHubTokens someTokens() {
        return new GitHubTokens("gho_access", "ghr_refresh", Instant.now().plusSeconds(3600), null);
    }

    @Override
    public String authorizeUrl(String state, String codeChallenge) {
        return "https://github.test/login/oauth/authorize?state=" + state + "&code_challenge=" + codeChallenge;
    }

    @Override
    public GitHubTokens refreshTokens(String refreshToken) {
        return new GitHubTokens("gho_refreshed", refreshToken, Instant.now().plusSeconds(3600), null);
    }

    @Override
    public GitHubTokens exchangeCode(String code, String codeVerifier) {
        GitHubTokens tokens = codes.get(code);
        if (tokens == null) {
            throw new IllegalArgumentException("fake에 등록되지 않은 code");
        }
        return tokens;
    }
}
