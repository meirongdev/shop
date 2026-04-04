package dev.meirong.shop.loyalty.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.common.kafka.RetryableKafkaConsumerException;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.BuyerRegisteredEventData;
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
public class BuyerRegisteredListener {

    private static final Logger log = LoggerFactory.getLogger(BuyerRegisteredListener.class);

    private final ObjectMapper objectMapper;
    private final LoyaltyAccountService accountService;
    private final OnboardingTaskService onboardingTaskService;
    private final IdempotencyGuard idempotencyGuard;
    private final LoyaltyIdempotencyKeyRepository idempotencyKeyRepository;

    public BuyerRegisteredListener(ObjectMapper objectMapper,
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
    @KafkaListener(topics = "${shop.loyalty.buyer-registered-topic}", groupId = "${spring.application.name}")
    @Transactional
    public void onBuyerRegistered(String payload) {
        try {
            EventEnvelope<BuyerRegisteredEventData> envelope = objectMapper.readValue(
                    payload, new TypeReference<>() {});
            validateEnvelope(envelope);
            BuyerRegisteredEventData data = envelope.data();
            String buyerId = data.buyerId();
            String idempotencyKey = "LOYALTY_BUYER_REGISTERED:" + buyerId;

            idempotencyGuard.executeOnce(
                    idempotencyKey,
                    () -> {
                        accountService.earnByRule(buyerId, "REGISTER", 1,
                                "register-" + buyerId, "Welcome bonus for registration");
                        onboardingTaskService.initForNewUser(buyerId);
                        idempotencyKeyRepository.save(new LoyaltyIdempotencyKeyEntity(idempotencyKey));
                        log.info("Processed USER_REGISTERED for buyer={}", buyerId);
                        return null;
                    },
                    () -> {
                        log.info("Loyalty registration bonus already processed for buyer={}, skipping", buyerId);
                        return null;
                    });
        } catch (JsonProcessingException exception) {
            throw new NonRetryableKafkaConsumerException("Malformed loyalty buyer registered event", exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new NonRetryableKafkaConsumerException("Invalid loyalty buyer registered event", exception);
        } catch (DataAccessException exception) {
            throw new RetryableKafkaConsumerException("Temporary loyalty registration processing failure", exception);
        }
    }

    @DltHandler
    public void handleDlt(String payload) {
        log.error("Loyalty buyer registered event sent to DLT: {}", payload);
    }

    private void validateEnvelope(EventEnvelope<BuyerRegisteredEventData> envelope) {
        if (envelope == null || envelope.data() == null) {
            throw new IllegalArgumentException("Loyalty registration event data is required");
        }
        envelope.assertSupportedSchema(EventEnvelope.CURRENT_SCHEMA_VERSION);
        if (!StringUtils.hasText(envelope.data().buyerId())) {
            throw new IllegalArgumentException("Loyalty registration buyerId is required");
        }
    }
}
