package dev.meirong.shop.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.idempotency.IdempotencyExempt;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.common.kafka.RetryableKafkaConsumerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@IdempotencyExempt(reason = "webhook_delivery enforces unique endpoint_id + event_id and retries reuse persisted delivery rows")
public class WebhookEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventListener.class);
    private final WebhookDeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    public WebhookEventListener(WebhookDeliveryService deliveryService, ObjectMapper objectMapper) {
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            autoCreateTopics = "true",
            exclude = NonRetryableKafkaConsumerException.class
    )
    @KafkaListener(topics = "${shop.webhook.order-events-topic}", groupId = "webhook-order-events")
    public void onOrderEvent(String message) {
        dispatchFromEnvelope(message, "order");
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            autoCreateTopics = "true",
            exclude = NonRetryableKafkaConsumerException.class
    )
    @KafkaListener(topics = "${shop.webhook.wallet-transactions-topic}", groupId = "webhook-wallet-events")
    public void onWalletEvent(String message) {
        dispatchFromEnvelope(message, "wallet");
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            autoCreateTopics = "true",
            exclude = NonRetryableKafkaConsumerException.class
    )
    @KafkaListener(topics = "${shop.webhook.user-registered-topic}", groupId = "webhook-user-events")
    public void onUserRegisteredEvent(String message) {
        dispatchFromEnvelope(message, "user");
    }

    @DltHandler
    public void handleDlt(String payload) {
        log.error("Webhook event sent to DLT: {}", payload);
    }

    private void dispatchFromEnvelope(String message, String defaultSource) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventId = requireText(node, "eventId", defaultSource + " eventId");
            String eventType = requireText(node, "type", defaultSource + " event type");
            deliveryService.dispatchEvent(eventType, eventId, message);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new NonRetryableKafkaConsumerException("Malformed webhook event", exception);
        } catch (IllegalArgumentException exception) {
            throw new NonRetryableKafkaConsumerException("Invalid webhook event", exception);
        } catch (DataAccessException exception) {
            throw new RetryableKafkaConsumerException("Temporary webhook event processing failure", exception);
        }
    }

    private String requireText(JsonNode node, String fieldName, String description) {
        if (node == null || !node.has(fieldName)) {
            throw new IllegalArgumentException(description + " is required");
        }
        String value = node.get(fieldName).asText();
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(description + " is required");
        }
        return value;
    }
}
