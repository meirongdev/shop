package dev.meirong.shop.authserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.authserver.config.AuthProperties;
import dev.meirong.shop.authserver.domain.UserAccountEntity;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.event.BuyerRegisteredEventData;
import dev.meirong.shop.contracts.event.EventEnvelope;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class BuyerRegisteredEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AuthProperties properties;

    public BuyerRegisteredEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                         ObjectMapper objectMapper,
                                         AuthProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void publish(UserAccountEntity account) {
        EventEnvelope<BuyerRegisteredEventData> envelope = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                "auth-server",
                "BUYER_REGISTERED",
                Instant.now(),
                new BuyerRegisteredEventData(account.getPrincipalId(), account.getUsername(), account.getEmail()));
        try {
            kafkaTemplate.send(properties.buyerRegisteredTopic(), objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Failed to serialize registration event", exception);
        }
    }
}
