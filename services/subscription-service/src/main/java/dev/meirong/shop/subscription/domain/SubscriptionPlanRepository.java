package dev.meirong.shop.subscription.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlanEntity, String> {
    List<SubscriptionPlanEntity> findBySellerId(String sellerId);
    List<SubscriptionPlanEntity> findByProductIdAndActiveTrue(String productId);
}
