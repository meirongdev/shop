package dev.meirong.shop.webhook.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpointEntity, String> {

    List<WebhookEndpointEntity> findBySellerId(String sellerId);

    List<WebhookEndpointEntity> findByActiveTrue();
}
