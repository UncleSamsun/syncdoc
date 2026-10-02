package io.github.unclesamsun.syncdoc.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.unclesamsun.syncdoc.github.GitHubIdentityGateway;
import io.github.unclesamsun.syncdoc.github.GitHubOAuthGateway;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicPathTest {
    @Test void validatesPrefixAndKeepsRedirectsInsideApplication() {
        var root = new PublicPath("");
        var nested = new PublicPath("/syncdoc");
        assertThat(root.resolve("/login?error=state")).isEqualTo("/login?error=state");
        assertThat(nested.resolve("/projects/p?x=1#s")).isEqualTo("/syncdoc/projects/p?x=1#s");
        assertThat(nested.resolve("//evil.example")).isEqualTo("/syncdoc/");
        assertThat(nested.resolve("/../grafana")).isEqualTo("/syncdoc/");
        assertThat(nested.resolve("/%2e%2e/grafana")).isEqualTo("/syncdoc/");
        assertThatThrownBy(() -> new PublicPath("//evil.example")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicPath("/../grafana")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void oauthStateFailureReturnsToPrefixedLogin() {
        var props = new AuthProperties("", null, null, true, "", null);
        var controller = new OAuthController(mock(GitHubOAuthGateway.class), mock(GitHubIdentityGateway.class),
            mock(LoginService.class), new SessionCookies(props), new OAuthStateCookie(props), new PublicPath("/syncdoc"));
        var response = controller.callback(null, null, new MockHttpServletRequest());
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/syncdoc/login?error=state");
    }
}
