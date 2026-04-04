package dev.meirong.shop.loyalty.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyRewardItemRepository extends JpaRepository<LoyaltyRewardItemEntity, String> {

    List<LoyaltyRewardItemEntity> findByActiveTrueOrderBySortOrderAsc();
}
