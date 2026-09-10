package io.github.unclesamsun.syncdoc.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class HealthEndpointTest extends PostgresContainerSupport {

    @Autowired
    TestRestTemplate rest;

    @Test
    void live_answers_200_without_touching_dependencies() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/health/live", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void ready_answers_200_when_database_is_reachable() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/health/ready", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"").contains("\"database\":\"UP\"");
    }
}
