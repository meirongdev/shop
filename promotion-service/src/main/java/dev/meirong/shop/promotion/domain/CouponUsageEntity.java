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
@Table(name = "coupon_usage")
public class CouponUsageEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "coupon_id", nullable = false, length = 36)
    private String couponId;

    @Column(name = "buyer_id", nullable = false, length = 64)
    private String buyerId;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "discount_applied", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountApplied;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    protected CouponUsageEntity() {
    }

    public CouponUsageEntity(String couponId, String buyerId, String orderId, BigDecimal discountApplied) {
        this.id = UUID.randomUUID().toString();
        this.couponId = couponId;
        this.buyerId = buyerId;
        this.orderId = orderId;
        this.discountApplied = discountApplied;
    }

    @PrePersist
    void prePersist() {
        this.usedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getCouponId() { return couponId; }
    public String getBuyerId() { return buyerId; }
    public String getOrderId() { return orderId; }
    public BigDecimal getDiscountApplied() { return discountApplied; }
    public Instant getUsedAt() { return usedAt; }
}
