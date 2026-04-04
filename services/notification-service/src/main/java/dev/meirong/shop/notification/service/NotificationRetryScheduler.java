package dev.meirong.shop.notification.service;

import dev.meirong.shop.common.metrics.MetricsHelper;
import dev.meirong.shop.notification.config.NotificationProperties;
import dev.meirong.shop.notification.domain.NotificationLogEntity;
import dev.meirong.shop.notification.domain.NotificationLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final MetricsHelper metrics;

    public NotificationRetryScheduler(NotificationLogRepository repository,
                                       NotificationApplicationService notificationService,
                                       NotificationProperties properties,
                                       MeterRegistry meterRegistry) {
        this.repository = repository;
        this.notificationService = notificationService;
        this.properties = properties;
        this.metrics = new MetricsHelper("notification-service", meterRegistry);
    }

    @Scheduled(fixedDelayString = "${shop.notification.retry-delay-ms:60000}")
    public void retryFailedNotifications() {
        List<NotificationLogEntity> failed = repository.findByStatusAndRetryCountLessThan(
                "FAILED", properties.retryMaxAttempts());

        if (!failed.isEmpty()) {
            log.info("Retrying {} failed notifications", failed.size());
            metrics.increment("shop_notification_retry_total",
                    "count", String.valueOf(failed.size()));
            for (NotificationLogEntity entry : failed) {
                notificationService.retryFailed(entry);
            }
        }
    }
}
