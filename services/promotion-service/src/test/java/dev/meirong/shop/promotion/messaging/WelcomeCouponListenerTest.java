package dev.meirong.shop.promotion.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.BuyerRegisteredEventData;
import dev.meirong.shop.promotion.domain.CouponEntity;
import dev.meirong.shop.promotion.domain.CouponRepository;
import dev.meirong.shop.promotion.domain.CouponTemplateEntity;
import dev.meirong.shop.promotion.domain.PromotionIdempotencyKeyEntity;
import dev.meirong.shop.promotion.domain.PromotionIdempotencyKeyRepository;
import dev.meirong.shop.promotion.service.CouponTemplateService;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WelcomeCouponListenerTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponTemplateService couponTemplateService;

    @Mock
    private PromotionIdempotencyKeyRepository promotionIdempotencyKeyRepository;

    @Mock
    private IdempotencyGuard idempotencyGuard;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private WelcomeCouponListener listener;

    @BeforeEach
    void setUp() {
        listener = new WelcomeCouponListener(
                objectMapper,
                couponRepository,
                couponTemplateService,
                promotionIdempotencyKeyRepository,
                idempotencyGuard
        );
        lenient().when(couponTemplateService.findTemplateByCode(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void onUserRegistered_firstCall_executesViaIdempotencyGuard() throws IOException {
        BuyerRegisteredEventData data = new BuyerRegisteredEventData("player-123", "testuser", "test@example.com");
        EventEnvelope<BuyerRegisteredEventData> event = new EventEnvelope<>(
                UUID.randomUUID().toString(), "auth-server", "BUYER_REGISTERED", Instant.now(), data);
        String payload = objectMapper.writeValueAsString(event);

        when(idempotencyGuard.executeOnce(anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Void> action = invocation.getArgument(1);
                    return action.get();
                });
        when(couponTemplateService.createTemplate(
                any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> new CouponTemplateEntity(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        invocation.getArgument(6),
                        invocation.getArgument(7),
                        invocation.getArgument(8),
                        invocation.getArgument(9)));
        when(couponRepository.save(any(CouponEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(promotionIdempotencyKeyRepository.save(any(PromotionIdempotencyKeyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        listener.onBuyerRegistered(payload);

        verify(idempotencyGuard).executeOnce(eq("WELCOME_COUPON:player-123"), any(), any());
        verify(couponRepository, times(3)).save(any(CouponEntity.class));
        verify(promotionIdempotencyKeyRepository).save(any(PromotionIdempotencyKeyEntity.class));
    }

    @Test
    void onUserRegistered_duplicateCall_usesIdempotencyGuardFallback() throws IOException {
        BuyerRegisteredEventData data = new BuyerRegisteredEventData("player-123", "testuser", "test@example.com");
        EventEnvelope<BuyerRegisteredEventData> event = new EventEnvelope<>(
                UUID.randomUUID().toString(), "auth-server", "BUYER_REGISTERED", Instant.now(), data);
        String payload = objectMapper.writeValueAsString(event);

        when(idempotencyGuard.executeOnce(anyString(), any(), any())).thenReturn(null);

        listener.onBuyerRegistered(payload);

        verify(couponRepository, never()).save(any(CouponEntity.class));
        verify(promotionIdempotencyKeyRepository, never()).save(any(PromotionIdempotencyKeyEntity.class));
    }

    @Test
    void onUserRegistered_existingTemplatesStillIssueInstances() throws IOException {
        BuyerRegisteredEventData data = new BuyerRegisteredEventData("player-123", "testuser", "test@example.com");
        EventEnvelope<BuyerRegisteredEventData> event = new EventEnvelope<>(
                UUID.randomUUID().toString(), "auth-server", "BUYER_REGISTERED", Instant.now(), data);
        String payload = objectMapper.writeValueAsString(event);
        CouponTemplateEntity existingTemplate = new CouponTemplateEntity(
                "SYSTEM", "WELCOME-5OFF", "$5 Off Welcome", "FIXED",
                java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO, null, 0, 1, 14
        );

        when(idempotencyGuard.executeOnce(anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Void> action = invocation.getArgument(1);
                    return action.get();
                });
        when(couponTemplateService.findTemplateByCode(anyString())).thenReturn(Optional.of(existingTemplate));
        when(couponRepository.save(any(CouponEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        listener.onBuyerRegistered(payload);

        verify(couponTemplateService, never()).createTemplate(
                any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt());
        verify(couponTemplateService, times(3))
                .issueToBuyerWithCode(anyString(), eq("player-123"), anyString(), any(Instant.class));
    }

    @Test
    void onUserRegistered_invalidPayload_throwsNonRetryableKafkaException() {
        assertThrows(NonRetryableKafkaConsumerException.class, () -> listener.onBuyerRegistered("not-json"));
    }
}
