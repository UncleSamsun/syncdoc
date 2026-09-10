package io.github.unclesamsun.syncdoc.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.auth.domain.SessionRepository;
import io.github.unclesamsun.syncdoc.auth.domain.UserEntity;
import io.github.unclesamsun.syncdoc.auth.domain.UserRepository;
import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SessionLifecycleTest extends PostgresContainerSupport {

    @Autowired
    TestRestTemplate rest;
    @Autowired
    UserRepository users;
    @Autowired
    SessionService sessions;
    @Autowired
    SessionRepository storedSessions;

    private String cookieFor(String githubUserId, String login) {
        Instant now = Instant.now();
        UserEntity user = users.save(new UserEntity(UUID.randomUUID(), githubUserId, login, now, now));
        return SessionService.COOKIE_NAME + "=" + sessions.issue(user.getId()).rawToken();
    }

    private static String rawTokenOf(String cookie) {
        return cookie.substring(cookie.indexOf('=') + 1);
    }

    private ResponseEntity<String> get(String path, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        if (cookie != null) {
            headers.add(HttpHeaders.COOKIE, cookie);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void me_returns_the_identity_and_a_csrf_token() {
        ResponseEntity<String> response = get("/api/v1/me", cookieFor("583231", "octocat"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"githubUserId\":\"583231\"")
                .contains("\"login\":\"octocat\"")
                .contains("\"serviceAdmin\":false")
                .contains("\"csrfToken\":\"");
    }

    @Test
    void an_unknown_cookie_value_is_not_a_session() {
        ResponseEntity<String> response = get("/api/v1/me", SessionService.COOKIE_NAME + "=made-up");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_without_the_csrf_header_is_rejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieFor("1001", "a"));
        ResponseEntity<String> response =
                rest.exchange("/api/v1/logout", HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("\"code\":\"CSRF_TOKEN_INVALID\"");
    }

    @Test
    void logout_with_the_csrf_header_invalidates_the_session() {
        String cookie = cookieFor("1002", "b");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        headers.add(CsrfTokenFilter.HEADER, sessions.csrfTokenFor(rawTokenOf(cookie)));

        ResponseEntity<String> logout =
                rest.exchange("/api/v1/logout", HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(get("/api/v1/me", cookie).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void the_raw_cookie_value_is_never_stored() {
        String cookie = cookieFor("1003", "c");
        String raw = rawTokenOf(cookie);
        assertThat(storedSessions.findByTokenHash(raw)).isEmpty();
        assertThat(sessions.authenticate(raw)).isPresent();
    }
}
