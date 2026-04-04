package dev.meirong.shop.activity.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActivityPlayerCardRepository extends JpaRepository<ActivityPlayerCard, String> {

    @Query("""
            SELECT COUNT(DISTINCT c.cardId)
            FROM ActivityPlayerCard c
            WHERE c.gameId = :gameId AND c.buyerId = :buyerId
            """)
    long countDistinctCardIdByGameIdAndBuyerId(String gameId, String buyerId);

    @Query("""
            SELECT COUNT(c)
            FROM ActivityPlayerCard c
            WHERE c.gameId = :gameId AND c.buyerId = :buyerId AND c.cardId = :cardId
            """)
    long countByGameIdAndBuyerIdAndCardId(String gameId, String buyerId, String cardId);

    List<ActivityPlayerCard> findByGameIdAndBuyerIdOrderByCreatedAtAsc(String gameId, String buyerId);
}
