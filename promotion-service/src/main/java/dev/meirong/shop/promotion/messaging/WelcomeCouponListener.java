package dev.meirong.shop.promotion.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.common.kafka.RetryableKafkaConsumerException;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.UserRegisteredEventData;
import dev.meirong.shop.promotion.domain.CouponEntity;
import dev.meirong.shop.promotion.domain.CouponTemplateEntity;
import dev.meirong.shop.promotion.domain.CouponRepository;
import dev.meirong.shop.promotion.domain.PromotionIdempotencyKeyEntity;
import dev.meirong.shop.promotion.domain.PromotionIdempotencyKeyRepository;
import dev.meirong.shop.promotion.service.CouponTemplateService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Issues welcome coupons when a new user registers.
 * Part of Epic 3.2 — New User Onboarding.
 */
@Component
public class WelcomeCouponListener {

    private static final Logger log = LoggerFactory.getLogger(WelcomeCouponListener.class);
    private static final String SYSTEM_SELLER = "SYSTEM";

    private final ObjectMapper objectMapper;
    private final CouponRepository couponRepository;
    private final CouponTemplateService couponTemplateService;
    private final PromotionIdempotencyKeyRepository promotionIdempotencyKeyRepository;
    private final IdempotencyGuard idempotencyGuard;

    public WelcomeCouponListener(ObjectMapper objectMapper, CouponRepository couponRepository,
                                 CouponTemplateService couponTemplateService,
                                 PromotionIdempotencyKeyRepository promotionIdempotencyKeyRepository,
                                 IdempotencyGuard idempotencyGuard) {
        this.objectMapper = objectMapper;
        this.couponRepository = couponRepository;
        this.couponTemplateService = couponTemplateService;
        this.promotionIdempotencyKeyRepository = promotionIdempotencyKeyRepository;
        this.idempotencyGuard = idempotencyGuard;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            autoCreateTopics = "true",
            exclude = NonRetryableKafkaConsumerException.class
    )
    @KafkaListener(topics = "${shop.kafka.user-registered-topic}", groupId = "${spring.application.name}")
    @Transactional
    public void onUserRegistered(String payload) {
        try {
            EventEnvelope<UserRegisteredEventData> envelope = objectMapper.readValue(
                    payload, new TypeReference<>() {});
            validateEnvelope(envelope);
            UserRegisteredEventData data = envelope.data();
            String playerId = data.playerId();
            String idempotencyKey = "WELCOME_COUPON:" + playerId;
            idempotencyGuard.executeOnce(
                    idempotencyKey,
                    () -> {
                        issueWelcomeCoupons(playerId, idempotencyKey);
                        return null;
                    },
                    () -> {
                        log.info("Welcome coupons already issued for player={}, skipping duplicate event", playerId);
                        return null;
                    });
        } catch (JsonProcessingException exception) {
            throw new NonRetryableKafkaConsumerException("Malformed welcome coupon registration event", exception);
        } catch (BusinessException | IllegalArgumentException exception) {
            throw new NonRetryableKafkaConsumerException("Invalid welcome coupon registration event", exception);
        } catch (DataAccessException exception) {
            throw new RetryableKafkaConsumerException("Temporary welcome coupon processing failure", exception);
        }
    }

    @DltHandler
    public void handleDlt(String payload) {
        log.error("Welcome coupon event sent to DLT: {}", payload);
    }

    private void issueWelcomeCoupons(String playerId, String idempotencyKey) {
        String baseCode = "WELCOME-" + playerId.substring(0, Math.min(8, playerId.length())).toUpperCase();
        Instant now = Instant.now();

        couponRepository.save(new CouponEntity(
                SYSTEM_SELLER,
                baseCode + "-5OFF",
                "FIXED",
                new BigDecimal("5.00"),
                BigDecimal.ZERO,
                null,
                1,
                now.plus(14, ChronoUnit.DAYS)));

        couponRepository.save(new CouponEntity(
                SYSTEM_SELLER,
                baseCode + "-SHIP",
                "FIXED",
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                null,
                1,
                now.plus(30, ChronoUnit.DAYS)));

        couponRepository.save(new CouponEntity(
                SYSTEM_SELLER,
                baseCode + "-9PCT",
                "PERCENTAGE",
                new BigDecimal("9.00"),
                BigDecimal.ZERO,
                new BigDecimal("20.00"),
                1,
                now.plus(14, ChronoUnit.DAYS)));

        createWelcomeInstances(playerId, baseCode, now);
        promotionIdempotencyKeyRepository.save(new PromotionIdempotencyKeyEntity(idempotencyKey));
        log.info("Issued 3 welcome coupons for new user: player={}", playerId);
    }

    private void createWelcomeInstances(String playerId, String baseCode, Instant now) {
        CouponTemplateEntity fiveOffTemplate = ensureWelcomeTemplate(
                "WELCOME-5OFF", "$5 Off Welcome", "FIXED", new BigDecimal("5.00"),
                BigDecimal.ZERO, null, 0, 1, 14);
        couponTemplateService.issueToBuyerWithCode(
                fiveOffTemplate.getId(), playerId, baseCode + "-5OFF-I", now.plus(14, ChronoUnit.DAYS));

        CouponTemplateEntity shippingTemplate = ensureWelcomeTemplate(
                "WELCOME-SHIP", "Free Shipping", "FIXED", new BigDecimal("10.00"),
                BigDecimal.ZERO, null, 0, 1, 30);
        couponTemplateService.issueToBuyerWithCode(
                shippingTemplate.getId(), playerId, baseCode + "-SHIP-I", now.plus(30, ChronoUnit.DAYS));

        CouponTemplateEntity percentageTemplate = ensureWelcomeTemplate(
                "WELCOME-9PCT", "9% Off Welcome", "PERCENTAGE", new BigDecimal("9.00"),
                BigDecimal.ZERO, new BigDecimal("20.00"), 0, 1, 14);
        couponTemplateService.issueToBuyerWithCode(
                percentageTemplate.getId(), playerId, baseCode + "-9PCT-I", now.plus(14, ChronoUnit.DAYS));
    }

    private CouponTemplateEntity ensureWelcomeTemplate(String code, String title, String discountType,
                                                       BigDecimal discountValue, BigDecimal minOrderAmount,
                                                       BigDecimal maxDiscount, int totalLimit,
                                                       int perUserLimit, int validDays) {
        CouponTemplateEntity existingTemplate = couponTemplateService.findTemplateByCode(code).orElse(null);
        if (existingTemplate != null) {
            return existingTemplate;
        }
        try {
            return couponTemplateService.createTemplate(
                    SYSTEM_SELLER,
                    code,
                    title,
                    discountType,
                    discountValue,
                    minOrderAmount,
                    maxDiscount,
                    totalLimit,
                    perUserLimit,
                    validDays
            );
        } catch (DataIntegrityViolationException exception) {
            return couponTemplateService.findTemplateByCode(code).orElseThrow(() -> exception);
        }
    }

    private void validateEnvelope(EventEnvelope<UserRegisteredEventData> envelope) {
        if (envelope == null || envelope.data() == null) {
            throw new IllegalArgumentException("Welcome coupon event data is required");
        }
        if (!StringUtils.hasText(envelope.data().playerId())) {
            throw new IllegalArgumentException("Welcome coupon event playerId is required");
        }
    }
}
