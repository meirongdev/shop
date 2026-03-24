package dev.meirong.shop.loyalty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "loyalty_account")
public class LoyaltyAccountEntity {

    @Id
    @Column(name = "player_id", nullable = false, length = 64)
    private String buyerId;

    @Column(name = "total_points", nullable = false)
    private long totalPoints;

    @Column(name = "used_points", nullable = false)
    private long usedPoints;

    @Column(nullable = false)
    private long balance;

    @Column(nullable = false, length = 20)
    private String tier;

    @Column(name = "tier_points", nullable = false)
    private long tierPoints;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LoyaltyAccountEntity() {
    }

    public LoyaltyAccountEntity(String buyerId) {
        this.buyerId = buyerId;
        this.totalPoints = 0;
        this.usedPoints = 0;
        this.balance = 0;
        this.tier = "SILVER";
        this.tierPoints = 0;
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

    public void earnPoints(long points) {
        this.balance += points;
        this.totalPoints += points;
        this.tierPoints += points;
        updateTier();
    }

    public void deductPoints(long points) {
        if (this.balance < points) {
            throw new IllegalStateException("Insufficient points: balance=" + balance + ", requested=" + points);
        }
        this.balance -= points;
        this.usedPoints += points;
    }

    public void expirePoints(long points) {
        this.balance = Math.max(0, this.balance - points);
    }

    private void updateTier() {
        if (this.tierPoints >= 10000) {
            this.tier = "PLATINUM";
        } else if (this.tierPoints >= 3000) {
            this.tier = "GOLD";
        } else {
            this.tier = "SILVER";
        }
    }

    public String getBuyerId() { return buyerId; }
    public long getTotalPoints() { return totalPoints; }
    public long getUsedPoints() { return usedPoints; }
    public long getBalance() { return balance; }
    public String getTier() { return tier; }
    public long getTierPoints() { return tierPoints; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
