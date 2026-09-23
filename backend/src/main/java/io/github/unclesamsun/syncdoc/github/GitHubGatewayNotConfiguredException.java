package io.github.unclesamsun.syncdoc.github;

public class GitHubGatewayNotConfiguredException extends IllegalStateException {

    public GitHubGatewayNotConfiguredException() {
        super("GitHub App 자격증명이 없다. SYNCDOC_GITHUB_APP_ID, SYNCDOC_GITHUB_CLIENT_ID, "
                + "SYNCDOC_GITHUB_CLIENT_SECRET, SYNCDOC_GITHUB_PRIVATE_KEY를 설정해야 한다.");
    }
}
