package dev.meirong.shop.notification.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.idempotency.IdempotencyExempt;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.common.kafka.RetryableKafkaConsumerException;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.OrderEventData;
import dev.meirong.shop.notification.service.NotificationApplicationService;
import java.util.HashMap;
import java.util.Map;
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
@IdempotencyExempt(reason = "NotificationApplicationService deduplicates notification_log rows by eventId and channel")
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private static final Map<String, String> STATUS_TO_EVENT_TYPE = Map.of(
            "PROCESSING", "ORDER_CONFIRMED",
            "SHIPPED",    "ORDER_SHIPPED",
            "COMPLETED",  "ORDER_COMPLETED",
            "CANCELLED",  "ORDER_CANCELLED"
    );

    private final ObjectMapper objectMapper;
    private final NotificationApplicationService notificationService;

    public OrderEventListener(ObjectMapper objectMapper,
                                NotificationApplicationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            autoCreateTopics = "true",
            exclude = NonRetryableKafkaConsumerException.class
    )
    @KafkaListener(topics = "${shop.notification.order-events-topic}",
                   groupId = "${spring.application.name}")
    public void onOrderEvent(String payload) {
        try {
            EventEnvelope<OrderEventData> event = objectMapper.readValue(
                    payload,
                    new TypeReference<>() {}
            );
            validateEvent(event);

            OrderEventData data = event.data();
            String eventType = STATUS_TO_EVENT_TYPE.get(data.status());
            if (eventType == null) {
                log.debug("Ignoring order event with status: {}", data.status());
                return;
            }

            log.info("Received order event: orderId={} status={}", data.orderId(), data.status());

            Map<String, Object> variables = new HashMap<>();
            variables.put("username", data.buyerId());
            variables.put("orderId", data.orderNo() != null ? data.orderNo() : data.orderId());
            variables.put("totalAmount", data.totalAmount());
            variables.put("items", data.items());

            // Buyer email not available in order events; use buyerId as recipient placeholder
            notificationService.processEvent(
                    event.eventId(),
                    eventType,
                    data.buyerId(),
                    null,
                    variables
            );
        } catch (JsonProcessingException exception) {
            throw new NonRetryableKafkaConsumerException("Malformed notification order event", exception);
        } catch (IllegalArgumentException exception) {
            throw new NonRetryableKafkaConsumerException("Invalid notification order event", exception);
        } catch (DataAccessException exception) {
            throw new RetryableKafkaConsumerException("Temporary notification order processing failure", exception);
        }
    }

    @DltHandler
    public void handleDlt(String payload) {
        log.error("Notification order event sent to DLT: {}", payload);
    }

    private void validateEvent(EventEnvelope<OrderEventData> event) {
        if (event == null || event.data() == null) {
            throw new IllegalArgumentException("Notification order event data is required");
        }
        event.assertSupportedSchema(EventEnvelope.CURRENT_SCHEMA_VERSION);
        OrderEventData data = event.data();
        if (!StringUtils.hasText(event.eventId())) {
            throw new IllegalArgumentException("Notification eventId is required");
        }
        if (!StringUtils.hasText(data.orderId())) {
            throw new IllegalArgumentException("Notification orderId is required");
        }
        if (!StringUtils.hasText(data.buyerId())) {
            throw new IllegalArgumentException("Notification buyerId is required");
        }
        if (!StringUtils.hasText(data.status())) {
            throw new IllegalArgumentException("Notification order status is required");
        }
    }
}
