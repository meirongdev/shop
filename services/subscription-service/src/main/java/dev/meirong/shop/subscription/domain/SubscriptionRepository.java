package dev.meirong.shop.subscription.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, String> {
    List<SubscriptionEntity> findByBuyerId(String buyerId);
    List<SubscriptionEntity> findByStatusAndNextRenewalAtBefore(String status, Instant before);
}
