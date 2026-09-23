package io.github.unclesamsun.syncdoc.sync;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.github.unclesamsun.syncdoc.common.ApiError;
import io.github.unclesamsun.syncdoc.common.ApiPaths;
import io.github.unclesamsun.syncdoc.project.domain.ProjectEntity;
import io.github.unclesamsun.syncdoc.project.domain.ProjectRepository;
import io.github.unclesamsun.syncdoc.sync.domain.WebhookDeliveryEntity;
import io.github.unclesamsun.syncdoc.sync.domain.WebhookDeliveryRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-021. GitHub webhook.
 *
 * <p>세션을 요구하지 않는 대신 raw body로 서명을 검사한다. 서명이 맞아야만 어떤 작업도 예약한다.
 * 중복 delivery는 delivery ID가 PK인 테이블이 막는다. 정상과 중복 모두 202다.
 */
@RestController
@RequestMapping(ApiPaths.BASE)
public class GitHubWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    /** 수집을 다시 돌릴 이유가 되는 이벤트만 받는다. 나머지는 받되 아무것도 예약하지 않는다. */
    private static final Set<String> COLLECT_EVENTS = Set.of("push");

    private final SyncQueue queue;
    private final ProjectRepository projects;
    private final WebhookDeliveryRepository deliveries;
    private final SyncProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public GitHubWebhookController(SyncQueue queue, ProjectRepository projects,
                                   WebhookDeliveryRepository deliveries, SyncProperties properties,
                                   ObjectMapper json, Clock clock) {
        this.queue = queue;
        this.projects = projects;
        this.deliveries = deliveries;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @PostMapping("/webhooks/github")
    public ResponseEntity<ApiError> receive(
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) byte[] body) {

        byte[] rawBody = body == null ? new byte[0] : body;
        if (rawBody.length > properties.maxPayloadBytes()) {
            return error(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "요청 본문이 너무 큽니다.");
        }
        if (!properties.isWebhookConfigured()) {
            // 비밀값이 없으면 서명을 확인할 수 없다. 확인하지 못한 요청을 받아들이지 않는다.
            return error(HttpStatus.SERVICE_UNAVAILABLE, "WEBHOOK_NOT_CONFIGURED",
                    "webhook을 확인할 수 없습니다.");
        }
        if (!WebhookSignature.matches(properties.webhookSecret(), rawBody, signature)) {
            return error(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "요청을 확인할 수 없습니다.");
        }
        if (deliveryId == null || deliveryId.isBlank()) {
            return error(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "요청을 확인할 수 없습니다.");
        }

        String eventName = event == null ? "unknown" : event;
        if (!recordDelivery(deliveryId, eventName)) {
            // 같은 delivery를 다시 받았다. 같은 작업을 두 번 예약하지 않는다.
            return ResponseEntity.accepted().build();
        }
        if (COLLECT_EVENTS.contains(eventName)) {
            projectOf(rawBody).ifPresent(project -> queue.request(project.getId(), false));
        }
        return ResponseEntity.accepted().build();
    }

    /** @return 처음 받은 delivery면 true */
    private boolean recordDelivery(String deliveryId, String event) {
        try {
            WebhookDeliveryEntity delivery =
                    new WebhookDeliveryEntity(deliveryId, event, clock.instant());
            delivery.processed(clock.instant());
            deliveries.saveAndFlush(delivery);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }

    /** 연결하지 않은 저장소의 이벤트는 조용히 무시한다. 존재 여부를 응답으로 구분하지 않는다. */
    private Optional<ProjectEntity> projectOf(byte[] rawBody) {
        try {
            JsonNode repository = json.readTree(rawBody).path("repository").path("id");
            if (repository.isMissingNode() || repository.isNull()) {
                return Optional.empty();
            }
            return projects.findByGithubRepositoryId(String.valueOf(repository.asLong()));
        } catch (Exception e) {
            log.debug("webhook 본문을 읽지 못했다");
            return Optional.empty();
        }
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ApiError.of(code, message, UUID.randomUUID().toString()));
    }
}
