package io.github.unclesamsun.syncdoc.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class UnauthenticatedAccessTest extends PostgresContainerSupport {

    @Autowired
    TestRestTemplate rest;

    @Test
    void me_without_session_is_401_with_contract_error_body() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/me", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(MediaType.APPLICATION_JSON.isCompatibleWith(response.getHeaders().getContentType())).isTrue();
        assertThat(response.getBody())
                .contains("\"code\":\"UNAUTHENTICATED\"")
                .contains("\"requestId\":\"")
                .contains("\"details\":{}");
        assertThat(String.valueOf(response.getHeaders().getFirst("WWW-Authenticate"))).doesNotContain("Basic");
    }

    @Test
    void unknown_protected_path_is_also_401_not_404() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/projects", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
