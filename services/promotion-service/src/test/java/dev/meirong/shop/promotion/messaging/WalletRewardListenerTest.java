package dev.meirong.shop.promotion.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.common.kafka.RetryableKafkaConsumerException;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.WalletTransactionEventData;
import dev.meirong.shop.promotion.domain.PromotionIdempotencyKeyRepository;
import dev.meirong.shop.promotion.service.PromotionApplicationService;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletRewardListenerTest {

    @Mock
    private PromotionApplicationService promotionApplicationService;
    @Mock
    private IdempotencyGuard idempotencyGuard;
    @Mock
    private PromotionIdempotencyKeyRepository idempotencyKeyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private WalletRewardListener listener;

    @BeforeEach
    void setUp() {
        // Pass-through: execute the action directly
        when(idempotencyGuard.executeOnce(anyString(), any(Supplier.class), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        listener = new WalletRewardListener(objectMapper, promotionApplicationService,
                idempotencyGuard, idempotencyKeyRepository);
    }

    @Test
    void onWalletEvent_depositCompleted_createsRewardOffer() throws IOException {
        String txId = UUID.randomUUID().toString();
        WalletTransactionEventData data = new WalletTransactionEventData(
                txId, "player-1", null, "DEPOSIT", new BigDecimal("50.00"), new BigDecimal("150.00"), "USD", "COMPLETED", Instant.now()
        );
        EventEnvelope<WalletTransactionEventData> event = new EventEnvelope<>(
                UUID.randomUUID().toString(), "wallet-service", "wallet.transaction.completed", Instant.now(), data
        );
        String payload = objectMapper.writeValueAsString(event);

        listener.onWalletEvent(payload);

        String expectedCode = "BONUS-" + txId.substring(0, 8);
        verify(promotionApplicationService).createWalletRewardOffer(
                eq(expectedCode),
                eq("Wallet Deposit Bonus"),
                anyString(),
                eq(new BigDecimal("20.00"))
        );
    }

    @Test
    void onWalletEvent_withdrawEvent_ignored() throws IOException {
        WalletTransactionEventData data = new WalletTransactionEventData(
                UUID.randomUUID().toString(), "player-1", null, "WITHDRAW", new BigDecimal("25.00"), new BigDecimal("75.00"), "USD", "COMPLETED", Instant.now()
        );
        EventEnvelope<WalletTransactionEventData> event = new EventEnvelope<>(
                UUID.randomUUID().toString(), "wallet-service", "wallet.transaction.completed", Instant.now(), data
        );
        String payload = objectMapper.writeValueAsString(event);

        listener.onWalletEvent(payload);

        verify(promotionApplicationService, never()).createWalletRewardOffer(anyString(), anyString(), anyString(), any(BigDecimal.class));
    }

    @Test
    void onWalletEvent_nonCompletedDeposit_ignored() throws IOException {
        WalletTransactionEventData data = new WalletTransactionEventData(
                UUID.randomUUID().toString(), "player-1", null, "DEPOSIT", new BigDecimal("50.00"), new BigDecimal("100.00"), "USD", "PENDING", Instant.now()
        );
        EventEnvelope<WalletTransactionEventData> event = new EventEnvelope<>(
                UUID.randomUUID().toString(), "wallet-service", "wallet.transaction.created", Instant.now(), data
        );
        String payload = objectMapper.writeValueAsString(event);

        listener.onWalletEvent(payload);

        verify(promotionApplicationService, never()).createWalletRewardOffer(anyString(), anyString(), anyString(), any(BigDecimal.class));
    }

    @Test
    void onWalletEvent_persistenceFailure_throwsRetryableKafkaConsumerException() throws IOException {
        String txId = UUID.randomUUID().toString();
        WalletTransactionEventData data = new WalletTransactionEventData(
                txId, "player-1", null, "DEPOSIT", new BigDecimal("50.00"), new BigDecimal("150.00"), "USD", "COMPLETED", Instant.now()
        );
        EventEnvelope<WalletTransactionEventData> event = new EventEnvelope<>(
                UUID.randomUUID().toString(), "wallet-service", "wallet.transaction.completed", Instant.now(), data
        );
        doThrow(new DataAccessResourceFailureException("db unavailable"))
                .when(promotionApplicationService)
                .createWalletRewardOffer(eq("BONUS-" + txId.substring(0, 8)), eq("Wallet Deposit Bonus"), anyString(), eq(new BigDecimal("20.00")));

        assertThrows(
                RetryableKafkaConsumerException.class,
                () -> listener.onWalletEvent(objectMapper.writeValueAsString(event))
        );
    }

    @Test
    void onWalletEvent_invalidPayload_throwsNonRetryableKafkaConsumerException() {
        assertThrows(NonRetryableKafkaConsumerException.class, () -> listener.onWalletEvent("not-json"));
    }
}
