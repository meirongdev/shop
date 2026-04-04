package dev.meirong.shop.loyalty.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyEarnRuleRepository extends JpaRepository<LoyaltyEarnRuleEntity, String> {

    Optional<LoyaltyEarnRuleEntity> findBySourceAndActiveTrue(String source);
}
