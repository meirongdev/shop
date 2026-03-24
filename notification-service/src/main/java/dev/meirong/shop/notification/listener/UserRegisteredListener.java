package dev.meirong.shop.notification.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.idempotency.IdempotencyExempt;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.common.kafka.RetryableKafkaConsumerException;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.UserRegisteredEventData;
import dev.meirong.shop.notification.service.NotificationApplicationService;
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
public class UserRegisteredListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredListener.class);

    private final ObjectMapper objectMapper;
    private final NotificationApplicationService notificationService;

    public UserRegisteredListener(ObjectMapper objectMapper,
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
    @KafkaListener(topics = "${shop.notification.user-registered-topic}",
                   groupId = "${spring.application.name}")
    public void onUserRegistered(String payload) {
        try {
            EventEnvelope<UserRegisteredEventData> event = objectMapper.readValue(
                    payload,
                    new TypeReference<>() {}
            );
            validateEvent(event);

            UserRegisteredEventData data = event.data();
            log.info("Received user.registered event: playerId={}", data.playerId());

            notificationService.processEvent(
                    event.eventId(),
                    "USER_REGISTERED",
                    data.playerId(),
                    data.email(),
                    Map.of("username", data.username())
            );
        } catch (JsonProcessingException exception) {
            throw new NonRetryableKafkaConsumerException("Malformed notification user registered event", exception);
        } catch (IllegalArgumentException exception) {
            throw new NonRetryableKafkaConsumerException("Invalid notification user registered event", exception);
        } catch (DataAccessException exception) {
            throw new RetryableKafkaConsumerException("Temporary notification user registered processing failure", exception);
        }
    }

    @DltHandler
    public void handleDlt(String payload) {
        log.error("Notification user registered event sent to DLT: {}", payload);
    }

    private void validateEvent(EventEnvelope<UserRegisteredEventData> event) {
        if (event == null || event.data() == null) {
            throw new IllegalArgumentException("Notification user registered event data is required");
        }
        UserRegisteredEventData data = event.data();
        if (!StringUtils.hasText(event.eventId())) {
            throw new IllegalArgumentException("Notification eventId is required");
        }
        if (!StringUtils.hasText(data.playerId())) {
            throw new IllegalArgumentException("Notification playerId is required");
        }
        if (!StringUtils.hasText(data.username())) {
            throw new IllegalArgumentException("Notification username is required");
        }
        if (!StringUtils.hasText(data.email())) {
            throw new IllegalArgumentException("Notification email is required");
        }
    }
}
