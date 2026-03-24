package dev.meirong.shop.subscription.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionOrderLogRepository extends JpaRepository<SubscriptionOrderLogEntity, String> {
    List<SubscriptionOrderLogEntity> findBySubscriptionId(String subscriptionId);
}
