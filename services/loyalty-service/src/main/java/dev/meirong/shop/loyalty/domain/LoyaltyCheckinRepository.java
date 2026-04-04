package dev.meirong.shop.loyalty.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyCheckinRepository extends JpaRepository<LoyaltyCheckinEntity, String> {

    Optional<LoyaltyCheckinEntity> findByBuyerIdAndCheckinDate(String buyerId, LocalDate date);

    List<LoyaltyCheckinEntity> findByBuyerIdAndCheckinDateBetweenOrderByCheckinDateAsc(
            String buyerId, LocalDate startDate, LocalDate endDate);

    long countByBuyerIdAndIsMakeupTrueAndCheckinDateBetween(
            String buyerId, LocalDate monthStart, LocalDate monthEnd);
}
