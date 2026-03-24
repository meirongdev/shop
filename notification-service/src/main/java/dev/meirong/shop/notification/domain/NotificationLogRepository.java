package dev.meirong.shop.notification.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, String> {

    boolean existsByEventIdAndChannel(String eventId, String channel);

    List<NotificationLogEntity> findByStatusAndRetryCountLessThan(String status, int maxRetries);
}
