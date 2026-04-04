package dev.meirong.shop.promotion.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.common.kafka.RetryableKafkaConsumerException;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.WalletTransactionEventData;
import dev.meirong.shop.promotion.domain.PromotionIdempotencyKeyEntity;
import dev.meirong.shop.promotion.domain.PromotionIdempotencyKeyRepository;
import dev.meirong.shop.promotion.service.PromotionApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class WalletRewardListener {

    private static final Logger log = LoggerFactory.getLogger(WalletRewardListener.class);

    private final ObjectMapper objectMapper;
    private final PromotionApplicationService promotionApplicationService;
    private final IdempotencyGuard idempotencyGuard;
    private final PromotionIdempotencyKeyRepository idempotencyKeyRepository;

    public WalletRewardListener(ObjectMapper objectMapper,
                                PromotionApplicationService promotionApplicationService,
                                IdempotencyGuard idempotencyGuard,
                                PromotionIdempotencyKeyRepository idempotencyKeyRepository) {
        this.objectMapper = objectMapper;
        this.promotionApplicationService = promotionApplicationService;
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
    @KafkaListener(topics = "${shop.kafka.wallet-topic}", groupId = "${spring.application.name}")
    @Transactional
    public void onWalletEvent(String payload) {
        try {
            EventEnvelope<WalletTransactionEventData> event = objectMapper.readValue(
                    payload,
                    new TypeReference<EventEnvelope<WalletTransactionEventData>>() {
                    }
            );
            validateEvent(event);
            WalletTransactionEventData data = event.data();
            if ("DEPOSIT".equalsIgnoreCase(data.type()) && "COMPLETED".equalsIgnoreCase(data.status())) {
                String idempotencyKey = "WALLET_REWARD:" + data.transactionId();
                idempotencyGuard.executeOnce(
                        idempotencyKey,
                        () -> {
                            String code = "BONUS-" + data.transactionId().substring(0, Math.min(8, data.transactionId().length()));
                            promotionApplicationService.createWalletRewardOffer(
                                    code,
                                    "Wallet Deposit Bonus",
                                    "Generated from wallet deposit event for " + data.buyerId(),
                                    data.amount().min(new java.math.BigDecimal("20.00"))
                            );
                            idempotencyKeyRepository.save(new PromotionIdempotencyKeyEntity(idempotencyKey));
                            return null;
                        },
                        () -> {
                            log.info("Wallet reward already processed for transactionId={}, skipping", data.transactionId());
                            return null;
                        });
            }
        } catch (JsonProcessingException exception) {
            throw new NonRetryableKafkaConsumerException("Malformed wallet reward event", exception);
        } catch (IllegalArgumentException exception) {
            throw new NonRetryableKafkaConsumerException("Invalid wallet reward event", exception);
        } catch (DataAccessException exception) {
            throw new RetryableKafkaConsumerException("Temporary wallet reward processing failure", exception);
        }
    }

    @DltHandler
    public void handleDlt(String payload) {
        log.error("Wallet reward event sent to DLT: {}", payload);
    }

    private void validateEvent(EventEnvelope<WalletTransactionEventData> event) {
        if (event == null || event.data() == null) {
            throw new IllegalArgumentException("Wallet reward event data is required");
        }
        event.assertSupportedSchema(EventEnvelope.CURRENT_SCHEMA_VERSION);
        WalletTransactionEventData data = event.data();
        if (!StringUtils.hasText(data.transactionId())) {
            throw new IllegalArgumentException("Wallet reward transactionId is required");
        }
        if (!StringUtils.hasText(data.buyerId())) {
            throw new IllegalArgumentException("Wallet reward buyerId is required");
        }
        if (!StringUtils.hasText(data.type())) {
            throw new IllegalArgumentException("Wallet reward type is required");
        }
        if (!StringUtils.hasText(data.status())) {
            throw new IllegalArgumentException("Wallet reward status is required");
        }
        if (data.amount() == null) {
            throw new IllegalArgumentException("Wallet reward amount is required");
        }
    }
}
