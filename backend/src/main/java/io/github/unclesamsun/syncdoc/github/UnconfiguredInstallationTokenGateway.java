package io.github.unclesamsun.syncdoc.github;

/** private key가 없으면 수집용 토큰을 만들 수 없다. 조용히 건너뛰지 않고 명시적으로 실패한다. */
public class UnconfiguredInstallationTokenGateway implements InstallationTokenGateway {

    @Override
    public String accessToken(String githubInstallationId) {
        throw new GitHubGatewayNotConfiguredException();
    }
}
