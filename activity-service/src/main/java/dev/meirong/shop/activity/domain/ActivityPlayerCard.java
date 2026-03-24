package dev.meirong.shop.activity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "activity_player_card")
public class ActivityPlayerCard {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "game_id", nullable = false, length = 36)
    private String gameId;

    @Column(name = "player_id", nullable = false, length = 64)
    private String buyerId;

    @Column(name = "card_id", nullable = false, length = 36)
    private String cardId;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivityPlayerCard() {}

    public ActivityPlayerCard(String id, String gameId, String buyerId, String cardId, String source) {
        this.id = id;
        this.gameId = gameId;
        this.buyerId = buyerId;
        this.cardId = cardId;
        this.source = source;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getGameId() { return gameId; }
    public String getBuyerId() { return buyerId; }
    public String getCardId() { return cardId; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
}
