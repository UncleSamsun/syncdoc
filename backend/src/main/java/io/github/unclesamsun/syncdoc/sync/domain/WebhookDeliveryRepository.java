package io.github.unclesamsun.syncdoc.sync.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, String> {
}
