package io.github.unclesamsun.syncdoc.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubApiIdentityGatewayTest {

    private MockRestServiceServer server;
    private GitHubApiIdentityGateway gateway;

    @BeforeEach
    void setUp() {
        GitHubProperties properties = new GitHubProperties("Iv23test", "secret-value", "4895456",
                "http://localhost:5173/api/v1/auth/github/callback",
                "https://github.test", "https://api.github.test");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new GitHubApiIdentityGateway(properties, builder);
    }

    @Test
    void the_authenticated_user_is_read_with_the_bearer_token() {
        server.expect(requestTo("https://api.github.test/user"))
                .andExpect(header("Authorization", "Bearer gho_a"))
                .andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
                .andRespond(withSuccess("{\"id\":79427050,\"login\":\"UncleSamsun\"}", MediaType.APPLICATION_JSON));

        GitHubUser user = gateway.fetchAuthenticatedUser("gho_a");

        server.verify();
        assertThat(user).isEqualTo(new GitHubUser("79427050", "UncleSamsun"));
    }

    /** 숫자 ID를 문자열로 보존한다. 계약이 JSON에서 문자열로 전달하기로 했다. */
    @Test
    void the_numeric_id_is_kept_as_a_string() {
        server.expect(requestTo("https://api.github.test/user"))
                .andRespond(withSuccess("{\"id\":1,\"login\":\"a\"}", MediaType.APPLICATION_JSON));

        assertThat(gateway.fetchAuthenticatedUser("gho_a").githubUserId()).isEqualTo("1");
    }

    @Test
    void a_login_is_resolved_to_a_stable_user_id() {
        server.expect(requestTo("https://api.github.test/users/teammate"))
                .andRespond(withSuccess("{\"id\":777,\"login\":\"teammate\"}", MediaType.APPLICATION_JSON));

        assertThat(gateway.fetchUserByLogin("teammate")).isEqualTo(new GitHubUser("777", "teammate"));
    }

    @Test
    void an_unknown_login_is_reported_as_not_found_not_as_a_generic_failure() {
        server.expect(requestTo("https://api.github.test/users/nobody"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> gateway.fetchUserByLogin("nobody"))
                .isInstanceOf(GitHubUserNotFoundException.class);
    }

    @Test
    void a_revoked_token_is_reported_as_a_lookup_failure_without_leaking_the_token() {
        server.expect(requestTo("https://api.github.test/user"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> gateway.fetchAuthenticatedUser("gho_secret"))
                .isInstanceOf(GitHubLookupFailedException.class)
                .hasMessageNotContaining("gho_secret");
    }
}
