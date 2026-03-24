package dev.meirong.shop.authserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.authserver.config.AuthProperties;
import dev.meirong.shop.authserver.domain.UserAccountEntity;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.UserRegisteredEventData;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserRegisteredEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AuthProperties properties;

    public UserRegisteredEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                        ObjectMapper objectMapper,
                                        AuthProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publish(UserAccountEntity account) {
        EventEnvelope<UserRegisteredEventData> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                "auth-server",
                "USER_REGISTERED",
                Instant.now(),
                new UserRegisteredEventData(account.getPrincipalId(), account.getUsername(), account.getEmail()));
        try {
            kafkaTemplate.send(properties.userRegisteredTopic(), objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Failed to serialize registration event", exception);
        }
    }
}
