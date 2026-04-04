package dev.meirong.shop.loyalty.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyRedemptionRepository extends JpaRepository<LoyaltyRedemptionEntity, String> {

    Page<LoyaltyRedemptionEntity> findByBuyerIdOrderByCreatedAtDesc(String buyerId, Pageable pageable);
}
