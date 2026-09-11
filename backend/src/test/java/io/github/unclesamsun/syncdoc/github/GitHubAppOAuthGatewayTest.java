package io.github.unclesamsun.syncdoc.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubAppOAuthGatewayTest {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final String TOKEN_URL = "https://github.test/login/oauth/access_token";

    private GitHubProperties properties;
    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private GitHubAppOAuthGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new GitHubProperties("Iv23test", "secret-value", "4895456", null,
                "http://localhost:5173/api/v1/auth/github/callback",
                "https://github.test", "https://api.github.test");
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new GitHubAppOAuthGateway(properties, builder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void the_authorize_url_carries_state_and_the_pkce_challenge() {
        String url = gateway.authorizeUrl("state-1", "challenge-1");

        assertThat(url).startsWith("https://github.test/login/oauth/authorize?");
        assertThat(url).contains("client_id=Iv23test");
        assertThat(url).contains("state=state-1");
        assertThat(url).contains("code_challenge=challenge-1");
        assertThat(url).contains("code_challenge_method=S256");
        assertThat(url).contains("redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fapi%2Fv1%2Fauth%2Fgithub%2Fcallback");
    }

    @Test
    void the_authorize_url_never_carries_the_client_secret() {
        assertThat(gateway.authorizeUrl("state-1", "challenge-1")).doesNotContain("secret-value");
    }

    @Test
    void exchanging_a_code_returns_the_tokens_with_absolute_expiry() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Accept", "application/json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code_verifier=verifier-1")))
                .andRespond(withSuccess("""
                        {"access_token":"gho_a","expires_in":28800,
                         "refresh_token":"ghr_b","refresh_token_expires_in":15811200,
                         "token_type":"bearer","scope":""}
                        """, MediaType.APPLICATION_JSON));

        GitHubTokens tokens = gateway.exchangeCode("code-1", "verifier-1");

        server.verify();
        assertThat(tokens.accessToken()).isEqualTo("gho_a");
        assertThat(tokens.refreshToken()).isEqualTo("ghr_b");
        assertThat(tokens.expiresAt()).isEqualTo(NOW.plusSeconds(28800));
        assertThat(tokens.refreshExpiresAt()).isEqualTo(NOW.plusSeconds(15811200));
    }

    @Test
    void a_token_without_expiry_fields_is_stored_without_expiry() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(
                "{\"access_token\":\"gho_a\",\"token_type\":\"bearer\",\"scope\":\"\"}",
                MediaType.APPLICATION_JSON));

        GitHubTokens tokens = gateway.exchangeCode("code-1", "verifier-1");

        assertThat(tokens.accessToken()).isEqualTo("gho_a");
        assertThat(tokens.refreshToken()).isNull();
        assertThat(tokens.expiresAt()).isNull();
    }

    /** GitHub는 교환에 실패해도 HTTP 200에 error 본문을 담아 돌려준다. 성공으로 읽으면 안 된다. */
    @Test
    void an_error_body_returned_with_http_200_is_a_failure() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withSuccess(
                "{\"error\":\"bad_verification_code\",\"error_description\":\"The code passed is incorrect\"}",
                MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.exchangeCode("code-1", "verifier-1"))
                .isInstanceOf(GitHubExchangeFailedException.class)
                .hasMessageContaining("bad_verification_code");
    }

    @Test
    void a_failure_message_never_leaks_the_client_secret_or_the_code() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.exchangeCode("code-1", "verifier-1"))
                .isInstanceOf(GitHubExchangeFailedException.class)
                .hasMessageNotContaining("secret-value")
                .hasMessageNotContaining("code-1");
    }

    @Test
    void refreshing_uses_the_refresh_grant_and_returns_new_tokens() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=refresh_token")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("refresh_token=ghr_old")))
                .andRespond(withSuccess("""
                        {"access_token":"gho_new","expires_in":28800,
                         "refresh_token":"ghr_new","refresh_token_expires_in":15811200,
                         "token_type":"bearer","scope":""}
                        """, MediaType.APPLICATION_JSON));

        GitHubTokens tokens = gateway.refreshTokens("ghr_old");

        server.verify();
        assertThat(tokens.accessToken()).isEqualTo("gho_new");
        assertThat(tokens.refreshToken()).isEqualTo("ghr_new");
    }
}
