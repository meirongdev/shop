package dev.meirong.shop.loyalty.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.http.TrustedHeaderNames;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.common.kafka.RetryableKafkaConsumerException;
import dev.meirong.shop.common.web.InternalSecurityProperties;
import dev.meirong.shop.contracts.api.ProfileInternalApi;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.OrderEventData;
import dev.meirong.shop.loyalty.config.LoyaltyProperties;
import dev.meirong.shop.loyalty.domain.LoyaltyIdempotencyKeyEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyIdempotencyKeyRepository;
import dev.meirong.shop.loyalty.service.LoyaltyAccountService;
import dev.meirong.shop.loyalty.service.OnboardingTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@EnableConfigurationProperties(LoyaltyProperties.class)
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ObjectMapper objectMapper;
    private final LoyaltyAccountService accountService;
    private final OnboardingTaskService onboardingTaskService;
    private final IdempotencyGuard idempotencyGuard;
    private final LoyaltyIdempotencyKeyRepository idempotencyKeyRepository;
    private final LoyaltyProperties loyaltyProperties;
    private final InternalSecurityProperties internalSecurityProperties;
    private final RestClient restClient;

    public OrderEventListener(ObjectMapper objectMapper,
                              LoyaltyAccountService accountService,
                              OnboardingTaskService onboardingTaskService,
                              IdempotencyGuard idempotencyGuard,
                              LoyaltyIdempotencyKeyRepository idempotencyKeyRepository,
                              LoyaltyProperties loyaltyProperties,
                              InternalSecurityProperties internalSecurityProperties,
                              RestClient.Builder builder) {
        this.objectMapper = objectMapper;
        this.accountService = accountService;
        this.onboardingTaskService = onboardingTaskService;
        this.idempotencyGuard = idempotencyGuard;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.loyaltyProperties = loyaltyProperties;
        this.internalSecurityProperties = internalSecurityProperties;
        this.restClient = builder.build();
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            autoCreateTopics = "true",
            exclude = NonRetryableKafkaConsumerException.class
    )
    @KafkaListener(topics = "${shop.loyalty.order-events-topic}", groupId = "${spring.application.name}")
    @Transactional
    public void onOrderEvent(String payload) {
        try {
            EventEnvelope<OrderEventData> envelope = objectMapper.readValue(
                    payload, new TypeReference<>() {});
            validateEnvelope(envelope);
            OrderEventData data = envelope.data();

            if ("ORDER_COMPLETED".equals(envelope.type())) {
                String idempotencyKey = "LOYALTY_ORDER_COMPLETED:" + data.orderId();
                idempotencyGuard.executeOnce(
                        idempotencyKey,
                        () -> {
                            handleOrderCompleted(data);
                            idempotencyKeyRepository.save(new LoyaltyIdempotencyKeyEntity(idempotencyKey));
                            return null;
                        },
                        () -> {
                            log.info("Loyalty order event already processed: orderId={}, skipping", data.orderId());
                            return null;
                        });
            }
        } catch (JsonProcessingException exception) {
            throw new NonRetryableKafkaConsumerException("Malformed loyalty order event", exception);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new NonRetryableKafkaConsumerException("Invalid loyalty order event", exception);
        } catch (DataAccessException | RestClientException exception) {
            throw new RetryableKafkaConsumerException("Temporary loyalty order processing failure", exception);
        }
    }

    @DltHandler
    public void handleDlt(String payload) {
        log.error("Loyalty order event sent to DLT: {}", payload);
    }

    private void handleOrderCompleted(OrderEventData data) {
        String playerId = data.buyerId();
        double amount = data.totalAmount() != null ? data.totalAmount().doubleValue() : 0;

        accountService.earnByRule(playerId, "PURCHASE", amount,
                "order-" + data.orderId(), "Purchase reward for order " + data.orderNo());

        try {
            onboardingTaskService.completeTask(playerId, "FIRST_ORDER");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.debug("Onboarding task FIRST_ORDER not applicable: {}", exception.getMessage());
        }

        ProfileInternalApi.ReferralRewardResult referralReward = resolveReferralReward(playerId);
        if (referralReward.rewardIssued() && referralReward.referrerId() != null) {
            accountService.earnPoints(
                    referralReward.referrerId(),
                    "REFERRAL_REWARD",
                    50,
                    "referral-" + data.orderId(),
                    "Referral reward for invitee first order " + data.orderNo());
        }

        log.info("Processed ORDER_COMPLETED for player={}, orderId={}", playerId, data.orderId());
    }

    private ProfileInternalApi.ReferralRewardResult resolveReferralReward(String inviteeId) {
        ApiResponse<ProfileInternalApi.ReferralRewardResult> response = restClient.post()
                .uri(loyaltyProperties.profileServiceUrl() + ProfileInternalApi.REFERRAL_FIRST_ORDER)
                .header(TrustedHeaderNames.INTERNAL_TOKEN, internalSecurityProperties.token())
                .body(new ProfileInternalApi.ReferralFirstOrderRequest(inviteeId))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (response == null || response.data() == null) {
            return new ProfileInternalApi.ReferralRewardResult(false, null);
        }
        return response.data();
    }

    private void validateEnvelope(EventEnvelope<OrderEventData> envelope) {
        if (envelope == null || envelope.data() == null) {
            throw new IllegalArgumentException("Loyalty order event data is required");
        }
        if (!StringUtils.hasText(envelope.data().orderId())) {
            throw new IllegalArgumentException("Loyalty orderId is required");
        }
        if (!StringUtils.hasText(envelope.data().buyerId())) {
            throw new IllegalArgumentException("Loyalty buyerId is required");
        }
        if (!StringUtils.hasText(envelope.type())) {
            throw new IllegalArgumentException("Loyalty order event type is required");
        }
    }
}
