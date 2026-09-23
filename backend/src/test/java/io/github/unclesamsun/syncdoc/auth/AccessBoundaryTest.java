package io.github.unclesamsun.syncdoc.auth;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.context.TestPropertySource;

/** 초대와 관리자 경계. 관리자 권한은 GitHub 열람 권한을 대신하지 않는다. */
@TestPropertySource(properties = "syncdoc.auth.admin-github-user-id=1")
class AccessBoundaryTest extends PostgresContainerSupport {

    @Autowired
    TestRestTemplate rest;
    @Autowired
    UserRepository users;
    @Autowired
    SessionService sessions;

    private String cookieFor(String githubUserId) {
        Instant now = Instant.now();
        UserEntity user = users.save(
                new UserEntity(UUID.randomUUID(), githubUserId, "u" + githubUserId, now, now));
        return SessionService.COOKIE_NAME + "=" + sessions.issue(user.getId()).rawToken();
    }

    private ResponseEntity<String> get(String path, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void the_configured_account_is_the_service_admin() {
        assertThat(get("/api/v1/me", cookieFor("1")).getBody()).contains("\"serviceAdmin\":true");
    }

    @Test
    void everyone_else_is_not() {
        assertThat(get("/api/v1/me", cookieFor("2")).getBody()).contains("\"serviceAdmin\":false");
    }

    @Test
    void a_non_admin_cannot_read_the_invitation_list() {
        ResponseEntity<String> response = get("/api/v1/invitations", cookieFor("3"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void an_admin_can_read_the_invitation_list() {
        assertThat(get("/api/v1/invitations", cookieFor("1")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void no_session_means_401_not_403() {
        ResponseEntity<String> response =
                rest.exchange("/api/v1/invitations", HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
