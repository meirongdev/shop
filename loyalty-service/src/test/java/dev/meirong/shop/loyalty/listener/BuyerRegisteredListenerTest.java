package dev.meirong.shop.loyalty.listener;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.common.kafka.RetryableKafkaConsumerException;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.BuyerRegisteredEventData;
import dev.meirong.shop.loyalty.domain.LoyaltyIdempotencyKeyEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyIdempotencyKeyRepository;
import dev.meirong.shop.loyalty.service.LoyaltyAccountService;
import dev.meirong.shop.loyalty.service.OnboardingTaskService;
import java.io.IOException;
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
class BuyerRegisteredListenerTest {

    @Mock
    private LoyaltyAccountService accountService;
    @Mock
    private OnboardingTaskService onboardingTaskService;
    @Mock
    private IdempotencyGuard idempotencyGuard;
    @Mock
    private LoyaltyIdempotencyKeyRepository idempotencyKeyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private BuyerRegisteredListener listener;

    @BeforeEach
    void setUp() {
        when(idempotencyGuard.executeOnce(anyString(), any(Supplier.class), any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        when(idempotencyKeyRepository.save(any(LoyaltyIdempotencyKeyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        listener = new BuyerRegisteredListener(
                objectMapper,
                accountService,
                onboardingTaskService,
                idempotencyGuard,
                idempotencyKeyRepository
        );
    }

    @Test
    void onUserRegistered_validEventProcessesRegistrationBonus() throws IOException {
        BuyerRegisteredEventData data = new BuyerRegisteredEventData("player-1", "testuser", "test@example.com");
        EventEnvelope<BuyerRegisteredEventData> event = new EventEnvelope<>(
                UUID.randomUUID().toString(), "auth-server", "BUYER_REGISTERED", Instant.now(), data
        );

        listener.onBuyerRegistered(objectMapper.writeValueAsString(event));

        verify(accountService).earnByRule(
                eq("player-1"),
                eq("REGISTER"),
                eq(1.0),
                eq("register-player-1"),
                eq("Welcome bonus for registration")
        );
        verify(onboardingTaskService).initForNewUser("player-1");
    }

    @Test
    void onUserRegistered_dataAccessFailureThrowsRetryableKafkaException() throws IOException {
        BuyerRegisteredEventData data = new BuyerRegisteredEventData("player-1", "testuser", "test@example.com");
        EventEnvelope<BuyerRegisteredEventData> event = new EventEnvelope<>(
                UUID.randomUUID().toString(), "auth-server", "BUYER_REGISTERED", Instant.now(), data
        );
        when(accountService.earnByRule(anyString(), eq("REGISTER"), anyDouble(), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("db unavailable"));

        assertThrows(
                RetryableKafkaConsumerException.class,
                () -> listener.onBuyerRegistered(objectMapper.writeValueAsString(event))
        );
    }

    @Test
    void onUserRegistered_invalidPayloadThrowsNonRetryableKafkaException() {
        assertThrows(NonRetryableKafkaConsumerException.class, () -> listener.onBuyerRegistered("not-json"));
    }
}
