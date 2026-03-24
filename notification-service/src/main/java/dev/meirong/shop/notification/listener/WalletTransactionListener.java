package dev.meirong.shop.notification.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.idempotency.IdempotencyExempt;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.common.kafka.RetryableKafkaConsumerException;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.WalletTransactionEventData;
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
public class WalletTransactionListener {

    private static final Logger log = LoggerFactory.getLogger(WalletTransactionListener.class);

    private final ObjectMapper objectMapper;
    private final NotificationApplicationService notificationService;

    public WalletTransactionListener(ObjectMapper objectMapper,
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
    @KafkaListener(topics = "${shop.notification.wallet-transactions-topic}",
                   groupId = "${spring.application.name}")
    public void onWalletTransaction(String payload) {
        try {
            EventEnvelope<WalletTransactionEventData> event = objectMapper.readValue(
                    payload,
                    new TypeReference<>() {}
            );
            validateEvent(event);

            WalletTransactionEventData data = event.data();

            String eventType = resolveEventType(data.type(), data.status());
            if (eventType == null) {
                log.debug("Ignoring wallet event: type={} status={}", data.type(), data.status());
                return;
            }

            log.info("Received wallet transaction event: transactionId={} type={}", data.transactionId(), eventType);

            // Use email from event if available, otherwise fallback
            String recipientEmail = data.email() != null ? data.email() : data.playerId() + "@shop.dev.meirong";

            Map<String, Object> variables = new HashMap<>();
            variables.put("username", data.playerId());
            variables.put("amount", data.amount());
            variables.put("balance", data.balance());
            variables.put("currency", data.currency());

            notificationService.processEvent(
                    event.eventId(),
                    eventType,
                    data.playerId(),
                    recipientEmail,
                    variables
            );
        } catch (JsonProcessingException exception) {
            throw new NonRetryableKafkaConsumerException("Malformed notification wallet event", exception);
        } catch (IllegalArgumentException exception) {
            throw new NonRetryableKafkaConsumerException("Invalid notification wallet event", exception);
        } catch (DataAccessException exception) {
            throw new RetryableKafkaConsumerException("Temporary notification wallet processing failure", exception);
        }
    }

    @DltHandler
    public void handleDlt(String payload) {
        log.error("Notification wallet event sent to DLT: {}", payload);
    }

    private String resolveEventType(String type, String status) {
        if (!"COMPLETED".equalsIgnoreCase(status)) return null;
        return switch (type.toUpperCase()) {
            case "DEPOSIT"  -> "DEPOSIT_COMPLETED";
            case "WITHDRAW" -> "WITHDRAWAL_COMPLETED";
            default -> null;
        };
    }

    private void validateEvent(EventEnvelope<WalletTransactionEventData> event) {
        if (event == null || event.data() == null) {
            throw new IllegalArgumentException("Notification wallet event data is required");
        }
        WalletTransactionEventData data = event.data();
        if (!StringUtils.hasText(event.eventId())) {
            throw new IllegalArgumentException("Notification eventId is required");
        }
        if (!StringUtils.hasText(data.transactionId())) {
            throw new IllegalArgumentException("Notification transactionId is required");
        }
        if (!StringUtils.hasText(data.playerId())) {
            throw new IllegalArgumentException("Notification playerId is required");
        }
        if (!StringUtils.hasText(data.type())) {
            throw new IllegalArgumentException("Notification wallet type is required");
        }
        if (!StringUtils.hasText(data.status())) {
            throw new IllegalArgumentException("Notification wallet status is required");
        }
    }
}
