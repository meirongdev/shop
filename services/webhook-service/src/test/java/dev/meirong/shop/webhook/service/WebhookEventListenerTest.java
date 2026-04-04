package dev.meirong.shop.webhook.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.common.kafka.RetryableKafkaConsumerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class WebhookEventListenerTest {

    @Mock
    private WebhookDeliveryService deliveryService;

    private WebhookEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new WebhookEventListener(deliveryService, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void onOrderEvent_validEnvelopeDispatchesWebhookDelivery() {
        String payload = """
                {"eventId":"evt-1","type":"ORDER_COMPLETED","data":{"orderId":"order-1"}}
                """;

        listener.onOrderEvent(payload);

        verify(deliveryService).dispatchEvent("ORDER_COMPLETED", "evt-1", payload);
    }

    @Test
    void onOrderEvent_missingEventIdThrowsNonRetryableKafkaException() {
        String payload = """
                {"type":"ORDER_COMPLETED","data":{"orderId":"order-1"}}
                """;

        assertThrows(NonRetryableKafkaConsumerException.class, () -> listener.onOrderEvent(payload));
    }

    @Test
    void onOrderEvent_dataAccessFailureThrowsRetryableKafkaException() {
        String payload = """
                {"eventId":"evt-1","type":"ORDER_COMPLETED","data":{"orderId":"order-1"}}
                """;
        doThrow(new DataAccessResourceFailureException("db unavailable"))
                .when(deliveryService)
                .dispatchEvent("ORDER_COMPLETED", "evt-1", payload);

        assertThrows(RetryableKafkaConsumerException.class, () -> listener.onOrderEvent(payload));
    }
}
