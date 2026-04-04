package dev.meirong.shop.promotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupon")
public class CouponEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "seller_id", nullable = false, length = 64)
    private String sellerId;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "discount_type", nullable = false, length = 32)
    private String discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "max_discount", precision = 19, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "usage_limit", nullable = false)
    private int usageLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CouponEntity() {
    }

    public CouponEntity(String sellerId, String code, String discountType, BigDecimal discountValue,
                        BigDecimal minOrderAmount, BigDecimal maxDiscount, int usageLimit, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.sellerId = sellerId;
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount != null ? minOrderAmount : BigDecimal.ZERO;
        this.maxDiscount = maxDiscount;
        this.usageLimit = usageLimit;
        this.usedCount = 0;
        this.expiresAt = expiresAt;
        this.active = true;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public void incrementUsedCount() {
        this.usedCount++;
    }

    public String getId() { return id; }
    public String getSellerId() { return sellerId; }
    public String getCode() { return code; }
    public String getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public int getUsageLimit() { return usageLimit; }
    public int getUsedCount() { return usedCount; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
