package dev.meirong.shop.activity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "activity_collect_card_def")
public class ActivityCollectCardDefinition {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "game_id", nullable = false, length = 36)
    private String gameId;

    @Column(name = "card_name", nullable = false, length = 64)
    private String cardName;

    @Column(nullable = false, length = 16)
    private String rarity;

    @Column(nullable = false, precision = 9, scale = 8)
    private BigDecimal probability;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivityCollectCardDefinition() {}

    public ActivityCollectCardDefinition(String id, String gameId, String cardName,
                                         String rarity, BigDecimal probability) {
        this.id = id;
        this.gameId = gameId;
        this.cardName = cardName;
        this.rarity = rarity;
        this.probability = probability;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getGameId() { return gameId; }
    public String getCardName() { return cardName; }
    public String getRarity() { return rarity; }
    public BigDecimal getProbability() { return probability; }
    public Instant getCreatedAt() { return createdAt; }
}
