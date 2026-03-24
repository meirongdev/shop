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
@Table(name = "coupon_instance")
public class CouponInstanceEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "template_id", nullable = false, length = 36)
    private String templateId;

    @Column(name = "buyer_id", nullable = false, length = 64)
    private String buyerId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "order_id", length = 36)
    private String orderId;

    @Column(name = "discount_applied", precision = 19, scale = 2)
    private BigDecimal discountApplied;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CouponInstanceEntity() {
    }

    public CouponInstanceEntity(String templateId, String buyerId, String code, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.templateId = templateId;
        this.buyerId = buyerId;
        this.code = code;
        this.status = "AVAILABLE";
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public void markUsed(String orderId, BigDecimal discountApplied) {
        this.status = "USED";
        this.orderId = orderId;
        this.discountApplied = discountApplied;
        this.usedAt = Instant.now();
    }

    public void markExpired() {
        this.status = "EXPIRED";
    }

    public boolean isAvailable() {
        return "AVAILABLE".equals(this.status) && Instant.now().isBefore(this.expiresAt);
    }

    public String getId() { return id; }
    public String getTemplateId() { return templateId; }
    public String getBuyerId() { return buyerId; }
    public String getCode() { return code; }
    public String getStatus() { return status; }
    public String getOrderId() { return orderId; }
    public BigDecimal getDiscountApplied() { return discountApplied; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
