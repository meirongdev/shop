package dev.meirong.shop.loyalty.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransactionEntity, String> {

    Page<LoyaltyTransactionEntity> findByBuyerIdOrderByCreatedAtDesc(String buyerId, Pageable pageable);

    List<LoyaltyTransactionEntity> findByReferenceId(String referenceId);

    @Query("SELECT DISTINCT t.buyerId FROM LoyaltyTransactionEntity t WHERE t.type = 'EARN' AND t.expired = false AND t.expireAt <= :today")
    List<String> findBuyerIdsWithExpirablePoints(LocalDate today);

    List<LoyaltyTransactionEntity> findByBuyerIdAndTypeAndExpiredFalseAndExpireAtLessThanEqual(String buyerId, String type, LocalDate today);
}
