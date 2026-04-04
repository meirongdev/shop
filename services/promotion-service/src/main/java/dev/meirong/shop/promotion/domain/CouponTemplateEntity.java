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
@Table(name = "coupon_template")
public class CouponTemplateEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "seller_id", nullable = false, length = 64)
    private String sellerId;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(name = "discount_type", nullable = false, length = 32)
    private String discountType;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "max_discount", precision = 19, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "total_limit", nullable = false)
    private int totalLimit;

    @Column(name = "per_user_limit", nullable = false)
    private int perUserLimit;

    @Column(name = "valid_days", nullable = false)
    private int validDays;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CouponTemplateEntity() {
    }

    public CouponTemplateEntity(String sellerId, String code, String title, String discountType,
                                 BigDecimal discountValue, BigDecimal minOrderAmount, BigDecimal maxDiscount,
                                 int totalLimit, int perUserLimit, int validDays) {
        this.id = UUID.randomUUID().toString();
        this.sellerId = sellerId;
        this.code = code;
        this.title = title != null ? title : "";
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount != null ? minOrderAmount : BigDecimal.ZERO;
        this.maxDiscount = maxDiscount;
        this.totalLimit = totalLimit;
        this.perUserLimit = perUserLimit;
        this.validDays = validDays;
        this.active = true;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getSellerId() { return sellerId; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDiscountType() { return discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public int getTotalLimit() { return totalLimit; }
    public int getPerUserLimit() { return perUserLimit; }
    public int getValidDays() { return validDays; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
