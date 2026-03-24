package dev.meirong.shop.activity.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActivityPlayerCardRepository extends JpaRepository<ActivityPlayerCard, String> {

    @Query("""
            SELECT COUNT(DISTINCT c.cardId)
            FROM ActivityPlayerCard c
            WHERE c.gameId = :gameId AND c.playerId = :playerId
            """)
    long countDistinctCardIdByGameIdAndPlayerId(String gameId, String playerId);

    @Query("""
            SELECT COUNT(c)
            FROM ActivityPlayerCard c
            WHERE c.gameId = :gameId AND c.playerId = :playerId AND c.cardId = :cardId
            """)
    long countByGameIdAndPlayerIdAndCardId(String gameId, String playerId, String cardId);

    List<ActivityPlayerCard> findByGameIdAndPlayerIdOrderByCreatedAtAsc(String gameId, String playerId);
}
