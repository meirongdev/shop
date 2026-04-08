package dev.meirong.shop.notification;

import dev.meirong.shop.notification.channel.ChannelDispatcher;
import dev.meirong.shop.notification.channel.NotificationChannel;
import dev.meirong.shop.notification.channel.NotificationRequest;
import dev.meirong.shop.notification.domain.NotificationLogEntity;
import dev.meirong.shop.notification.domain.NotificationLogRepository;
import dev.meirong.shop.notification.service.NotificationApplicationService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationApplicationServiceTest {

    @Mock
    private NotificationLogRepository repository;

    @Mock
    private MeterRegistry meterRegistry;

    private NotificationApplicationService service;

    @BeforeEach
    void setUp() {
        NotificationChannel emailChannel = new NotificationChannel() {
            @Override
            public String channelType() { return "EMAIL"; }

            @Override
            public void send(NotificationRequest request) {
                // no-op for test
            }
        };

        ChannelDispatcher dispatcher = new ChannelDispatcher(List.of(emailChannel), meterRegistry);
        service = new NotificationApplicationService(repository, dispatcher);
    }

    @Test
    void processEvent_userRegistered_createsLogAndSends() {
        when(repository.existsByEventIdAndChannel("evt-1", "EMAIL")).thenReturn(false);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.processEvent("evt-1", "BUYER_REGISTERED", "player-1001",
                "test@example.com", Map.of("username", "Alice"));

        ArgumentCaptor<NotificationLogEntity> captor = ArgumentCaptor.forClass(NotificationLogEntity.class);
        verify(repository, times(2)).save(captor.capture());

        NotificationLogEntity saved = captor.getAllValues().getLast();
        assertEquals("SENT", saved.getStatus());
        assertEquals("welcome-email", saved.getTemplateCode());
        assertEquals("test@example.com", saved.getRecipientAddr());
    }

    @Test
    void processEvent_duplicateEvent_skips() {
        when(repository.existsByEventIdAndChannel("evt-1", "EMAIL")).thenReturn(true);

        service.processEvent("evt-1", "BUYER_REGISTERED", "player-1001",
                "test@example.com", Map.of("username", "Alice"));

        verify(repository, never()).save(any());
    }

    @Test
    void processEvent_unknownEventType_ignores() {
        service.processEvent("evt-1", "UNKNOWN_EVENT", "player-1001",
                "test@example.com", Map.of());

        verify(repository, never()).existsByEventIdAndChannel(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void processEvent_sendFailure_marksFailed() {
        NotificationChannel failingChannel = new NotificationChannel() {
            @Override
            public String channelType() { return "EMAIL"; }

            @Override
            public void send(NotificationRequest request) {
                throw new RuntimeException("SMTP connection refused");
            }
        };

        ChannelDispatcher failingDispatcher = new ChannelDispatcher(List.of(failingChannel), meterRegistry);
        NotificationApplicationService failingService =
                new NotificationApplicationService(repository, failingDispatcher);

        when(repository.existsByEventIdAndChannel("evt-2", "EMAIL")).thenReturn(false);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        failingService.processEvent("evt-2", "ORDER_CONFIRMED", "player-1001",
                "test@example.com", Map.of("username", "Bob", "orderId", "ORD-1", "totalAmount", "99.99"));

        ArgumentCaptor<NotificationLogEntity> captor = ArgumentCaptor.forClass(NotificationLogEntity.class);
        verify(repository, times(2)).save(captor.capture());

        NotificationLogEntity saved = captor.getAllValues().getLast();
        assertEquals("FAILED", saved.getStatus());
        assertEquals(1, saved.getRetryCount());
        assertTrue(saved.getErrorMessage().contains("SMTP connection refused"));
    }
}
