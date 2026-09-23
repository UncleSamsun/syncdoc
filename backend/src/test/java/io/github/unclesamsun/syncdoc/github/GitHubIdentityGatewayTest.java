package io.github.unclesamsun.syncdoc.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GitHubIdentityGatewayTest extends PostgresContainerSupport {

    @Autowired
    GitHubIdentityGateway gateway;

    @Test
    void without_credentials_the_default_gateway_fails_loudly_instead_of_pretending() {
        assertThat(gateway).isInstanceOf(UnconfiguredGitHubIdentityGateway.class);
        assertThatThrownBy(() -> gateway.fetchAuthenticatedUser("any"))
                .isInstanceOf(GitHubGatewayNotConfiguredException.class)
                .hasMessageContaining("SYNCDOC_GITHUB_APP_ID");
    }

    @Test
    void fake_returns_the_registered_user_by_token() {
        FakeGitHubIdentityGateway fake = new FakeGitHubIdentityGateway();
        fake.register("tok-1", new GitHubUser("583231", "octocat"));
        assertThat(fake.fetchAuthenticatedUser("tok-1")).isEqualTo(new GitHubUser("583231", "octocat"));
    }
}
