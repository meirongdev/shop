package dev.meirong.shop.loyalty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "loyalty_redemption")
public class LoyaltyRedemptionEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "player_id", nullable = false, length = 64)
    private String buyerId;

    @Column(name = "reward_item_id", nullable = false, length = 36)
    private String rewardItemId;

    @Column(name = "reward_name", nullable = false, length = 128)
    private String rewardName;

    @Column(name = "points_spent", nullable = false)
    private long pointsSpent;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "coupon_code", length = 64)
    private String couponCode;

    @Column(name = "order_id", length = 36)
    private String orderId;

    @Column(length = 256)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LoyaltyRedemptionEntity() {
    }

    public LoyaltyRedemptionEntity(String buyerId, String rewardItemId, String rewardName,
                                   long pointsSpent, int quantity, String type) {
        this.id = UUID.randomUUID().toString();
        this.buyerId = buyerId;
        this.rewardItemId = rewardItemId;
        this.rewardName = rewardName;
        this.pointsSpent = pointsSpent;
        this.quantity = quantity;
        this.status = "PROCESSING";
        this.type = type;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markCompleted(String couponCode, String orderId) {
        this.status = "COMPLETED";
        this.couponCode = couponCode;
        this.orderId = orderId;
    }

    public void markFailed(String remark) {
        this.status = "FAILED";
        this.remark = remark;
    }

    public String getId() { return id; }
    public String getBuyerId() { return buyerId; }
    public String getRewardItemId() { return rewardItemId; }
    public String getRewardName() { return rewardName; }
    public long getPointsSpent() { return pointsSpent; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public String getType() { return type; }
    public String getCouponCode() { return couponCode; }
    public String getOrderId() { return orderId; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
