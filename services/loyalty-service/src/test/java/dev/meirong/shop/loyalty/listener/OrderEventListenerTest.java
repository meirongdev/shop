package dev.meirong.shop.loyalty.listener;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.loyalty.config.LoyaltyProperties;
import dev.meirong.shop.loyalty.domain.LoyaltyIdempotencyKeyRepository;
import dev.meirong.shop.loyalty.service.LoyaltyAccountService;
import dev.meirong.shop.loyalty.service.OnboardingTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private LoyaltyAccountService accountService;
    @Mock
    private OnboardingTaskService onboardingTaskService;
    @Mock
    private IdempotencyGuard idempotencyGuard;
    @Mock
    private LoyaltyIdempotencyKeyRepository idempotencyKeyRepository;
    @Mock
    private RestClient.Builder builder;
    @Mock
    private RestClient restClient;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private OrderEventListener listener;

    @BeforeEach
    void setUp() {
        when(builder.build()).thenReturn(restClient);
        listener = new OrderEventListener(
                objectMapper,
                accountService,
                onboardingTaskService,
                idempotencyGuard,
                idempotencyKeyRepository,
                new LoyaltyProperties("orders", "users", "http://profile-service", 10, 1, 3),
                builder
        );
    }

    @Test
    void onOrderEvent_invalidPayloadThrowsNonRetryableKafkaException() {
        assertThrows(NonRetryableKafkaConsumerException.class, () -> listener.onOrderEvent("not-json"));
    }
}
