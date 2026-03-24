package dev.meirong.shop.webhook.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, String> {

    List<WebhookDeliveryEntity> findByEndpointId(String endpointId);

    List<WebhookDeliveryEntity> findByStatusAndNextRetryAtBefore(String status, Instant before);

    List<WebhookDeliveryEntity> findByStatusIn(List<String> statuses);
}
