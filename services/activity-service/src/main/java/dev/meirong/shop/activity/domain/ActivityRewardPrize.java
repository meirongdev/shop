package dev.meirong.shop.activity.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "activity_reward_prize")
public class ActivityRewardPrize {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "game_id", nullable = false, length = 36)
    private String gameId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private PrizeType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal value = BigDecimal.ZERO;

    @Column(name = "coupon_template_id", length = 36)
    private String couponTemplateId;

    @Column(name = "total_stock", nullable = false)
    private int totalStock = -1;

    @Column(name = "remaining_stock", nullable = false)
    private int remainingStock = -1;

    @Column(nullable = false, precision = 9, scale = 8)
    private BigDecimal probability = BigDecimal.ZERO;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    protected ActivityRewardPrize() {}

    public ActivityRewardPrize(String id, String gameId, String name, PrizeType type) {
        this.id = id;
        this.gameId = gameId;
        this.name = name;
        this.type = type;
    }

    public boolean hasStock() {
        return totalStock == -1 || remainingStock > 0;
    }

    public boolean decrementStock() {
        if (totalStock == -1) return true;
        if (remainingStock <= 0) return false;
        this.remainingStock--;
        return true;
    }

    // Getters and setters
    public String getId() { return id; }
    public String getGameId() { return gameId; }
    public String getName() { return name; }
    public PrizeType getType() { return type; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public String getCouponTemplateId() { return couponTemplateId; }
    public void setCouponTemplateId(String couponTemplateId) { this.couponTemplateId = couponTemplateId; }
    public int getTotalStock() { return totalStock; }
    public void setTotalStock(int totalStock) { this.totalStock = totalStock; }
    public int getRemainingStock() { return remainingStock; }
    public void setRemainingStock(int remainingStock) { this.remainingStock = remainingStock; }
    public BigDecimal getProbability() { return probability; }
    public void setProbability(BigDecimal probability) { this.probability = probability; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
