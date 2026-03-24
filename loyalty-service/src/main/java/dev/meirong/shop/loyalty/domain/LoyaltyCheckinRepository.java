package dev.meirong.shop.loyalty.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyCheckinRepository extends JpaRepository<LoyaltyCheckinEntity, String> {

    Optional<LoyaltyCheckinEntity> findByPlayerIdAndCheckinDate(String playerId, LocalDate date);

    List<LoyaltyCheckinEntity> findByPlayerIdAndCheckinDateBetweenOrderByCheckinDateAsc(
            String playerId, LocalDate startDate, LocalDate endDate);

    long countByPlayerIdAndIsMakeupTrueAndCheckinDateBetween(
            String playerId, LocalDate monthStart, LocalDate monthEnd);
}
