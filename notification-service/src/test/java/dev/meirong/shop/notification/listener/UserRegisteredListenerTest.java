package dev.meirong.shop.notification.listener;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.kafka.NonRetryableKafkaConsumerException;
import dev.meirong.shop.common.kafka.RetryableKafkaConsumerException;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.UserRegisteredEventData;
import dev.meirong.shop.notification.service.NotificationApplicationService;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class UserRegisteredListenerTest {

    @Mock
    private NotificationApplicationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private UserRegisteredListener listener;

    @BeforeEach
    void setUp() {
        listener = new UserRegisteredListener(objectMapper, notificationService);
    }

    @Test
    void onUserRegistered_validEventDelegatesToNotificationService() throws IOException {
        UserRegisteredEventData data = new UserRegisteredEventData("player-1", "testuser", "test@example.com");
        EventEnvelope<UserRegisteredEventData> event = new EventEnvelope<>(
                UUID.randomUUID().toString(), "auth-server", "USER_REGISTERED", Instant.now(), data
        );

        listener.onUserRegistered(objectMapper.writeValueAsString(event));

        verify(notificationService).processEvent(
                eq(event.eventId()),
                eq("USER_REGISTERED"),
                eq("player-1"),
                eq("test@example.com"),
                anyMap()
        );
    }

    @Test
    void onUserRegistered_persistenceFailureThrowsRetryableKafkaException() throws IOException {
        UserRegisteredEventData data = new UserRegisteredEventData("player-1", "testuser", "test@example.com");
        EventEnvelope<UserRegisteredEventData> event = new EventEnvelope<>(
                UUID.randomUUID().toString(), "auth-server", "USER_REGISTERED", Instant.now(), data
        );
        doThrow(new DataAccessResourceFailureException("db unavailable"))
                .when(notificationService)
                .processEvent(eq(event.eventId()), eq("USER_REGISTERED"), eq("player-1"), eq("test@example.com"), anyMap());

        assertThrows(
                RetryableKafkaConsumerException.class,
                () -> listener.onUserRegistered(objectMapper.writeValueAsString(event))
        );
    }

    @Test
    void onUserRegistered_invalidPayloadThrowsNonRetryableKafkaException() {
        assertThrows(NonRetryableKafkaConsumerException.class, () -> listener.onUserRegistered("not-json"));
    }
}
