package dev.meirong.shop.notification.service;

import dev.meirong.shop.notification.channel.ChannelDispatcher;
import dev.meirong.shop.notification.channel.NotificationRequest;
import dev.meirong.shop.notification.domain.NotificationLogEntity;
import dev.meirong.shop.notification.domain.NotificationLogRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationApplicationService.class);

    private final NotificationLogRepository repository;
    private final ChannelDispatcher channelDispatcher;

    public NotificationApplicationService(NotificationLogRepository repository,
                                           ChannelDispatcher channelDispatcher) {
        this.repository = repository;
        this.channelDispatcher = channelDispatcher;
    }

    @Transactional
    public void processEvent(String eventId, String eventType, String recipientId,
                              String recipientEmail, Map<String, Object> variables) {
        NotificationRouteConfig route = NotificationRouteConfig.resolve(eventType);
        if (route == null) {
            log.debug("No notification route for event type: {}", eventType);
            return;
        }

        if (repository.existsByEventIdAndChannel(eventId, route.channel())) {
            log.debug("Notification already sent: eventId={} channel={}", eventId, route.channel());
            return;
        }

        String logId = generateUlid();
        NotificationLogEntity logEntry = new NotificationLogEntity(
                logId, eventId, eventType, recipientId,
                route.channel(), recipientEmail, route.templateCode(), route.subject()
        );

        repository.save(logEntry);

        try {
            channelDispatcher.dispatch(route.channel(), new NotificationRequest(
                    recipientEmail, route.templateCode(), route.subject(), variables
            ));
            logEntry.markSent();
            log.info("Notification sent: eventId={} type={} to={}", eventId, eventType, recipientEmail);
        } catch (RuntimeException exception) {
            logEntry.markFailed(exception.getMessage());
            log.error("Notification failed: eventId={} type={} to={}", eventId, eventType, recipientEmail, exception);
        }

        repository.save(logEntry);
    }

    @Transactional
    public void retryFailed(NotificationLogEntity logEntry) {
        NotificationRouteConfig route = NotificationRouteConfig.resolve(logEntry.getEventType());
        if (route == null) return;

        logEntry.resetToPending();
        try {
            channelDispatcher.dispatch(route.channel(), new NotificationRequest(
                    logEntry.getRecipientAddr(), logEntry.getTemplateCode(),
                    logEntry.getSubject(), Map.of()
            ));
            logEntry.markSent();
            log.info("Retry succeeded: id={} eventId={}", logEntry.getId(), logEntry.getEventId());
        } catch (RuntimeException exception) {
            logEntry.markFailed(exception.getMessage());
            log.error("Retry failed: id={} eventId={}", logEntry.getId(), logEntry.getEventId(), exception);
        }
        repository.save(logEntry);
    }

    private String generateUlid() {
        // Simple ULID-like ID: timestamp prefix + random suffix (26 chars)
        long timestamp = System.currentTimeMillis();
        String timePart = Long.toString(timestamp, 32);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 26 - timePart.length());
        return timePart + randomPart;
    }
}
