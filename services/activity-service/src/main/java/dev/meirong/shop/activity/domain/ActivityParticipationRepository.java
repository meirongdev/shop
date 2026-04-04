package dev.meirong.shop.activity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ActivityParticipationRepository extends JpaRepository<ActivityParticipation, String> {

    List<ActivityParticipation> findByGameIdAndBuyerId(String gameId, String buyerId);

    @Query("SELECT COUNT(p) FROM ActivityParticipation p WHERE p.gameId = :gameId AND p.buyerId = :buyerId")
    long countByGameIdAndBuyerId(String gameId, String buyerId);

    @Query("SELECT COUNT(p) FROM ActivityParticipation p WHERE p.gameId = :gameId AND p.buyerId = :buyerId AND p.participatedAt >= :since")
    long countByGameIdAndBuyerIdSince(String gameId, String buyerId, Instant since);

    @Query("SELECT p FROM ActivityParticipation p WHERE p.rewardStatus = 'PENDING' AND p.result = 'WIN' AND p.participatedAt < :threshold")
    List<ActivityParticipation> findPendingRewards(Instant threshold);

    @Query("SELECT COUNT(p) FROM ActivityParticipation p WHERE p.gameId = :gameId AND p.result = 'WIN'")
    long countWinningParticipationsByGameId(String gameId);

    List<ActivityParticipation> findByGameIdAndBuyerIdOrderByParticipatedAtDesc(String gameId, String buyerId);
}
