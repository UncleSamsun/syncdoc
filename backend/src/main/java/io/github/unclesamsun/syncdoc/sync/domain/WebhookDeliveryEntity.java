package io.github.unclesamsun.syncdoc.sync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 서명을 검증한 delivery 하나. delivery ID가 PK라 같은 delivery가 두 번 들어오면 저장에서 막힌다.
 * 중복을 코드로 판단하지 않고 제약으로 막는 이유는 동시에 도착한 재전송도 막아야 하기 때문이다.
 *
 * <p>raw payload와 서명은 저장하지 않는다.
 */
@Entity
@Table(name = "webhook_deliveries")
public class WebhookDeliveryEntity {

    @Id
    @Column(name = "delivery_id", nullable = false, updatable = false)
    private String deliveryId;

    @Column(nullable = false, updatable = false)
    private String event;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected WebhookDeliveryEntity() {
    }

    public WebhookDeliveryEntity(String deliveryId, String event, Instant receivedAt) {
        this.deliveryId = deliveryId;
        this.event = event;
        this.receivedAt = receivedAt;
    }

    public String getDeliveryId() {
        return deliveryId;
    }

    public String getEvent() {
        return event;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void processed(Instant at) {
        this.processedAt = at;
    }
}
