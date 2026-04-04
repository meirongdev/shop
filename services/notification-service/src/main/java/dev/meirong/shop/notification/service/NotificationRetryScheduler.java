package dev.meirong.shop.notification.service;

import dev.meirong.shop.notification.config.NotificationProperties;
import dev.meirong.shop.notification.domain.NotificationLogEntity;
import dev.meirong.shop.notification.domain.NotificationLogRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryScheduler.class);

    private final NotificationLogRepository repository;
    private final NotificationApplicationService notificationService;
    private final NotificationProperties properties;

    public NotificationRetryScheduler(NotificationLogRepository repository,
                                       NotificationApplicationService notificationService,
                                       NotificationProperties properties) {
        this.repository = repository;
        this.notificationService = notificationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${shop.notification.retry-delay-ms:60000}")
    public void retryFailedNotifications() {
        List<NotificationLogEntity> failed = repository.findByStatusAndRetryCountLessThan(
                "FAILED", properties.retryMaxAttempts());

        if (!failed.isEmpty()) {
            log.info("Retrying {} failed notifications", failed.size());
            for (NotificationLogEntity entry : failed) {
                notificationService.retryFailed(entry);
            }
        }
    }
}
