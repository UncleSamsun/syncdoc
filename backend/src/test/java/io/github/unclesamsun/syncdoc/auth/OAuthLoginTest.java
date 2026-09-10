package io.github.unclesamsun.syncdoc.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.auth.domain.InvitationEntity;
import io.github.unclesamsun.syncdoc.auth.domain.InvitationRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.github.FakeGitHubIdentityGateway;
import io.github.unclesamsun.syncdoc.github.FakeGitHubOAuthGateway;
import io.github.unclesamsun.syncdoc.github.GitHubIdentityGateway;
import io.github.unclesamsun.syncdoc.github.GitHubOAuthGateway;
import io.github.unclesamsun.syncdoc.github.GitHubUser;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Import(OAuthLoginTest.Gateways.class)
class OAuthLoginTest extends PostgresContainerSupport {

    @TestConfiguration
    static class Gateways {

        @Bean
        @Primary
        GitHubOAuthGateway fakeOAuth() {
            return new FakeGitHubOAuthGateway();
        }

        @Bean
        @Primary
        GitHubIdentityGateway fakeIdentity() {
            return new FakeGitHubIdentityGateway();
        }
    }

    /**
     * 302를 따라가지 않는 클라이언트다. 기본 클라이언트는 redirect를 자동으로 따라가
     * 테스트가 실제 github.test에 접속하려 하고, Location과 Set-Cookie도 볼 수 없다.
     */
    private final RestTemplate rest = new RestTemplate(new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()));

    @Value("${local.server.port}")
    int port;
    @Autowired
    GitHubOAuthGateway oauth;
    @Autowired
    GitHubIdentityGateway identity;
    @Autowired
    InvitationRepository invitations;
    @Autowired
    UserRepository users;

    private FakeGitHubOAuthGateway fakeOAuth() {
        return (FakeGitHubOAuthGateway) oauth;
    }

    private FakeGitHubIdentityGateway fakeIdentity() {
        return (FakeGitHubIdentityGateway) identity;
    }

    private void invite(String githubUserId) {
        invitations.save(new InvitationEntity(UUID.randomUUID(), githubUserId, null, Instant.now()));
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<Void> start(String returnTo) {
        String path = "/api/v1/auth/github/start" + (returnTo == null ? "" : "?returnTo=" + returnTo);
        return rest.exchange(url(path), HttpMethod.GET, HttpEntity.EMPTY, Void.class);
    }

    private ResponseEntity<Void> callback(String code, String state, List<String> setCookies) {
        HttpHeaders headers = new HttpHeaders();
        if (setCookies != null) {
            setCookies.forEach(c -> headers.add(HttpHeaders.COOKIE, c.split(";", 2)[0]));
        }
        return rest.exchange(url("/api/v1/auth/github/callback?code=" + code + "&state=" + state),
                HttpMethod.GET, new HttpEntity<>(headers), Void.class);
    }

    private static String stateOf(ResponseEntity<Void> started) {
        URI location = started.getHeaders().getLocation();
        for (String pair : location.getQuery().split("&")) {
            if (pair.startsWith("state=")) {
                return pair.substring("state=".length());
            }
        }
        throw new IllegalStateException("state가 없다: " + location);
    }

    private static boolean hasSessionCookie(ResponseEntity<Void> response) {
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        return setCookies != null && setCookies.stream()
                .anyMatch(c -> c.startsWith(SessionService.COOKIE_NAME + "=")
                        && !c.startsWith(SessionService.COOKIE_NAME + "=;"));
    }

    @Test
    void start_redirects_to_github_with_a_state_cookie() {
        ResponseEntity<Void> started = start(null);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(started.getHeaders().getLocation().toString()).startsWith("https://github.test/");
        assertThat(started.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anySatisfy(c -> assertThat(c).contains(OAuthStateCookie.NAME).contains("HttpOnly"));
    }

    @Test
    void an_invited_account_gets_a_session_and_lands_on_the_requested_path() {
        invite("583231");
        fakeIdentity().register("gho_access", new GitHubUser("583231", "octocat"));
        fakeOAuth().register("code-1", fakeOAuth().someTokens());

        ResponseEntity<Void> started = start("/projects/x");
        ResponseEntity<Void> done = callback("code-1", stateOf(started),
                started.getHeaders().get(HttpHeaders.SET_COOKIE));

        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(done.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/projects/x");
        assertThat(hasSessionCookie(done)).isTrue();
        assertThat(users.findByGithubUserId("583231")).isPresent();
    }

    @Test
    void an_uninvited_account_gets_no_session_and_goes_to_the_uninvited_path() {
        fakeIdentity().register("gho_access", new GitHubUser("999", "stranger"));
        fakeOAuth().register("code-2", fakeOAuth().someTokens());

        ResponseEntity<Void> started = start(null);
        ResponseEntity<Void> done = callback("code-2", stateOf(started),
                started.getHeaders().get(HttpHeaders.SET_COOKIE));

        assertThat(done.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/uninvited");
        assertThat(hasSessionCookie(done)).isFalse();
        assertThat(users.findByGithubUserId("999")).isEmpty();
    }

    @Test
    void a_state_that_does_not_match_the_cookie_is_rejected() {
        invite("583231");
        fakeIdentity().register("gho_access", new GitHubUser("583231", "octocat"));
        fakeOAuth().register("code-3", fakeOAuth().someTokens());

        ResponseEntity<Void> started = start(null);
        ResponseEntity<Void> done = callback("code-3", "not-the-state",
                started.getHeaders().get(HttpHeaders.SET_COOKIE));

        assertThat(done.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/login?error=state");
        assertThat(hasSessionCookie(done)).isFalse();
    }

    @Test
    void a_state_cannot_be_used_twice() {
        invite("583231");
        fakeIdentity().register("gho_access", new GitHubUser("583231", "octocat"));
        fakeOAuth().register("code-4", fakeOAuth().someTokens());

        ResponseEntity<Void> started = start(null);
        String state = stateOf(started);
        callback("code-4", state, started.getHeaders().get(HttpHeaders.SET_COOKIE));

        // 콜백이 state 쿠키를 지우므로, 쿠키 없이 같은 state를 다시 내면 거절이다.
        ResponseEntity<Void> replay = callback("code-4", state, List.of());
        assertThat(replay.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/login?error=state");
        assertThat(hasSessionCookie(replay)).isFalse();
    }

    @Test
    void an_external_return_to_is_ignored() {
        invite("583231");
        fakeIdentity().register("gho_access", new GitHubUser("583231", "octocat"));
        fakeOAuth().register("code-5", fakeOAuth().someTokens());

        ResponseEntity<Void> started = start("https://evil.test/steal");
        ResponseEntity<Void> done = callback("code-5", stateOf(started),
                started.getHeaders().get(HttpHeaders.SET_COOKIE));

        assertThat(done.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/");
    }

    @Test
    void a_renamed_account_stays_the_same_user() {
        invite("583231");
        fakeOAuth().register("code-6", fakeOAuth().someTokens());
        fakeIdentity().register("gho_access", new GitHubUser("583231", "octocat"));
        ResponseEntity<Void> first = start(null);
        callback("code-6", stateOf(first), first.getHeaders().get(HttpHeaders.SET_COOKIE));
        UUID idBefore = users.findByGithubUserId("583231").orElseThrow().getId();

        fakeOAuth().register("code-7", fakeOAuth().someTokens());
        fakeIdentity().register("gho_access", new GitHubUser("583231", "octocat-renamed"));
        ResponseEntity<Void> second = start(null);
        callback("code-7", stateOf(second), second.getHeaders().get(HttpHeaders.SET_COOKIE));

        assertThat(users.findAll()).hasSize(1);
        assertThat(users.findByGithubUserId("583231").orElseThrow().getId()).isEqualTo(idBefore);
        assertThat(users.findByGithubUserId("583231").orElseThrow().getLogin()).isEqualTo("octocat-renamed");
    }
}
