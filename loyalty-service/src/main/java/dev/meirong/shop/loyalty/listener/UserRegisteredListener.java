package dev.meirong.shop.loyalty.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.common.kafka.RetryableKafkaConsumerException;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.UserRegisteredEventData;
import dev.meirong.shop.loyalty.domain.LoyaltyIdempotencyKeyEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyIdempotencyKeyRepository;
import dev.meirong.shop.loyalty.config.LoyaltyProperties;
import dev.meirong.shop.loyalty.service.LoyaltyAccountService;
import dev.meirong.shop.loyalty.service.OnboardingTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@EnableConfigurationProperties(LoyaltyProperties.class)
public class UserRegisteredListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredListener.class);

    private final ObjectMapper objectMapper;
    private final LoyaltyAccountService accountService;
    private final OnboardingTaskService onboardingTaskService;
    private final IdempotencyGuard idempotencyGuard;
    private final LoyaltyIdempotencyKeyRepository idempotencyKeyRepository;

    public UserRegisteredListener(ObjectMapper objectMapper,
                                  LoyaltyAccountService accountService,
                                  OnboardingTaskService onboardingTaskService,
                                  IdempotencyGuard idempotencyGuard,
                                  LoyaltyIdempotencyKeyRepository idempotencyKeyRepository) {
        this.objectMapper = objectMapper;
        this.accountService = accountService;
        this.onboardingTaskService = onboardingTaskService;
        this.idempotencyGuard = idempotencyGuard;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            autoCreateTopics = "true",
            exclude = NonRetryableKafkaConsumerException.class
    )
    @KafkaListener(topics = "${shop.loyalty.user-registered-topic}", groupId = "${spring.application.name}")
    @Transactional
    public void onUserRegistered(String payload) {
        try {
            EventEnvelope<UserRegisteredEventData> envelope = objectMapper.readValue(
                    payload, new TypeReference<>() {});
            validateEnvelope(envelope);
            UserRegisteredEventData data = envelope.data();
            String playerId = data.playerId();
            String idempotencyKey = "LOYALTY_USER_REGISTERED:" + playerId;

            idempotencyGuard.executeOnce(
                    idempotencyKey,
                    () -> {
                        accountService.earnByRule(playerId, "REGISTER", 1,
                                "register-" + playerId, "Welcome bonus for registration");
                        onboardingTaskService.initForNewUser(playerId);
                        idempotencyKeyRepository.save(new LoyaltyIdempotencyKeyEntity(idempotencyKey));
                        log.info("Processed USER_REGISTERED for player={}", playerId);
                        return null;
                    },
                    () -> {
                        log.info("Loyalty registration bonus already processed for player={}, skipping", playerId);
                        return null;
                    });
        } catch (JsonProcessingException exception) {
            throw new NonRetryableKafkaConsumerException("Malformed loyalty user registered event", exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new NonRetryableKafkaConsumerException("Invalid loyalty user registered event", exception);
        } catch (DataAccessException exception) {
            throw new RetryableKafkaConsumerException("Temporary loyalty registration processing failure", exception);
        }
    }

    @DltHandler
    public void handleDlt(String payload) {
        log.error("Loyalty user registered event sent to DLT: {}", payload);
    }

    private void validateEnvelope(EventEnvelope<UserRegisteredEventData> envelope) {
        if (envelope == null || envelope.data() == null) {
            throw new IllegalArgumentException("Loyalty registration event data is required");
        }
        if (!StringUtils.hasText(envelope.data().playerId())) {
            throw new IllegalArgumentException("Loyalty registration playerId is required");
        }
    }
}
