package dev.meirong.shop.webhook.service;

import dev.meirong.shop.webhook.config.WebhookProperties;
import dev.meirong.shop.webhook.domain.WebhookDeliveryEntity;
import dev.meirong.shop.webhook.domain.WebhookDeliveryRepository;
import dev.meirong.shop.webhook.domain.WebhookEndpointEntity;
import dev.meirong.shop.webhook.domain.WebhookEndpointRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookProperties properties;
    private final HttpClient httpClient;

    public WebhookDeliveryService(WebhookEndpointRepository endpointRepository,
                                   WebhookDeliveryRepository deliveryRepository,
                                   WebhookProperties properties) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.deliveryTimeoutSeconds()))
                .build();
    }

    @Transactional
    public void dispatchEvent(String eventType, String eventId, String payload) {
        List<WebhookEndpointEntity> endpoints = endpointRepository.findByActiveTrue();
        for (WebhookEndpointEntity endpoint : endpoints) {
            if (!endpoint.matchesEventType(eventType)) {
                continue;
            }
            WebhookDeliveryEntity delivery = WebhookDeliveryEntity.create(
                    endpoint.getId(), eventType, eventId, payload);
            deliveryRepository.save(delivery);
            deliver(delivery, endpoint);
        }
    }

    private void deliver(WebhookDeliveryEntity delivery, WebhookEndpointEntity endpoint) {
        String signature = WebhookSigner.sign(delivery.getPayload(), endpoint.getSecret());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.getUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Signature", signature)
                    .header("X-Webhook-Event", delivery.getEventType())
                    .header("X-Webhook-Delivery-Id", delivery.getId())
                    .timeout(Duration.ofSeconds(properties.deliveryTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayload()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.markSuccess(response.statusCode(), response.body());
                log.info("Webhook delivered: endpoint={} event={}", endpoint.getId(), delivery.getEventType());
            } else {
                delivery.markFailed(response.statusCode(), response.body(),
                        properties.retryMaxAttempts(), properties.retryIntervalSeconds());
                log.warn("Webhook delivery failed: endpoint={} status={}",
                        endpoint.getId(), response.statusCode());
            }
        } catch (IOException | InterruptedException | IllegalArgumentException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            delivery.markFailedNoResponse(exception.getMessage(),
                    properties.retryMaxAttempts(), properties.retryIntervalSeconds());
            log.warn("Webhook delivery error: endpoint={} error={}", endpoint.getId(), exception.getMessage());
        }
        deliveryRepository.save(delivery);
    }

    @Scheduled(fixedDelayString = "${shop.webhook.retry-interval-seconds:60}000")
    @Transactional
    public void retryFailedDeliveries() {
        List<WebhookDeliveryEntity> retryable = deliveryRepository
                .findByStatusAndNextRetryAtBefore(WebhookDeliveryEntity.STATUS_RETRYING, Instant.now());
        if (retryable.isEmpty()) return;

        log.info("Retrying {} webhook deliveries", retryable.size());
        for (WebhookDeliveryEntity delivery : retryable) {
            endpointRepository.findById(delivery.getEndpointId()).ifPresent(endpoint -> {
                if (endpoint.isActive()) {
                    deliver(delivery, endpoint);
                }
            });
        }
    }
}
