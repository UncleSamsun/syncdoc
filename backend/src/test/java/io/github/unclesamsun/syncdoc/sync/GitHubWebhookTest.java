package io.github.unclesamsun.syncdoc.sync;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.unclesamsun.syncdoc.support.PostgresContainerSupport;
import io.github.unclesamsun.syncdoc.support.ProjectFixture;
import io.github.unclesamsun.syncdoc.sync.domain.SyncJobRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * API-021. 세션 없이 받는 유일한 쓰기 경로이므로 서명이 방어선 전부다.
 *
 * <p>중복 delivery가 같은 작업을 두 번 예약하지 않는 것도 여기서 확인한다. GitHub는 응답이 늦으면
 * 같은 delivery를 다시 보낸다.
 */
@TestPropertySource(properties = "syncdoc.sync.max-payload-bytes=2048")
class GitHubWebhookTest extends PostgresContainerSupport {

    private static final String SECRET = "test-webhook-secret";

    @Autowired
    TestRestTemplate rest;
    @Autowired
    SyncJobRepository jobs;
    @Autowired
    SyncQueue queue;
    @Autowired
    ProjectFixture fixture;

    private ResponseEntity<String> send(String event, String delivery, String body, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-GitHub-Event", event);
        headers.add("X-GitHub-Delivery", delivery);
        if (signature != null) {
            headers.add("X-Hub-Signature-256", signature);
        }
        return rest.exchange("/api/v1/webhooks/github", HttpMethod.POST,
                new HttpEntity<>(body.getBytes(StandardCharsets.UTF_8), headers), String.class);
    }

    private ResponseEntity<String> signedSend(String event, String delivery, String body) {
        String signature = "sha256=" + WebhookSignature.hmacSha256(SECRET, body.getBytes(StandardCharsets.UTF_8));
        return send(event, delivery, body, signature);
    }

    private static String pushPayload(String githubRepositoryId) {
        return "{\"ref\":\"refs/heads/main\",\"repository\":{\"id\":" + githubRepositoryId
                + ",\"full_name\":\"o/r\"}}";
    }

    @Test
    void a_push_with_a_valid_signature_schedules_one_collection() {
        UUID project = fixture.newProject("501").getId();

        ResponseEntity<String> response = signedSend("push", "d-501", pushPayload("501"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(queue.activeJob(project)).isPresent();
    }

    @Test
    void a_wrong_signature_is_rejected_and_schedules_nothing() {
        UUID project = fixture.newProject("502").getId();

        ResponseEntity<String> response = send("push", "d-502", pushPayload("502"),
                "sha256=0000000000000000000000000000000000000000000000000000000000000000");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(queue.activeJob(project)).isEmpty();
    }

    @Test
    void a_missing_signature_is_rejected() {
        fixture.newProject("503");

        assertThat(send("push", "d-503", pushPayload("503"), null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void a_signature_over_a_different_body_does_not_pass() {
        UUID project = fixture.newProject("504").getId();
        String signature = "sha256=" + WebhookSignature.hmacSha256(SECRET,
                pushPayload("504").getBytes(StandardCharsets.UTF_8));

        ResponseEntity<String> response = send("push", "d-504", pushPayload("999"), signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(queue.activeJob(project)).isEmpty();
    }

    @Test
    void the_same_delivery_twice_schedules_only_one_job() {
        UUID project = fixture.newProject("505").getId();

        assertThat(signedSend("push", "d-505", pushPayload("505")).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        // GitHub가 재전송한 같은 delivery다.
        assertThat(signedSend("push", "d-505", pushPayload("505")).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        assertThat(jobs.findByProjectIdOrderByCreatedAtDesc(project)).hasSize(1);
    }

    @Test
    void an_event_we_do_not_collect_on_schedules_nothing() {
        UUID project = fixture.newProject("506").getId();

        assertThat(signedSend("issues", "d-506", pushPayload("506")).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(queue.activeJob(project)).isEmpty();
    }

    @Test
    void an_event_for_a_repository_nobody_connected_is_accepted_and_ignored() {
        assertThat(signedSend("push", "d-507", pushPayload("999999")).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(jobs.findAll()).isEmpty();
    }

    @Test
    void a_body_over_the_limit_is_refused_with_413() {
        UUID project = fixture.newProject("508").getId();
        String big = "{\"filler\":\"" + "x".repeat(4096) + "\",\"repository\":{\"id\":508}}";

        assertThat(signedSend("push", "d-508", big).getStatusCode())
                .isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(queue.activeJob(project)).isEmpty();
    }
}
