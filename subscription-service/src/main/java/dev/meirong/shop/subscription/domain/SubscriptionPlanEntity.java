package dev.meirong.shop.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_plan")
public class SubscriptionPlanEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "seller_id", nullable = false, length = 64)
    private String sellerId;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 32)
    private String frequency;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SubscriptionPlanEntity() {
    }

    public static SubscriptionPlanEntity create(String sellerId, String productId,
                                                  String name, String description,
                                                  BigDecimal price, String frequency) {
        SubscriptionPlanEntity entity = new SubscriptionPlanEntity();
        entity.id = UUID.randomUUID().toString();
        entity.sellerId = sellerId;
        entity.productId = productId;
        entity.name = name;
        entity.description = description;
        entity.price = price;
        entity.frequency = frequency;
        entity.active = true;
        return entity;
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

    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }

    public String getId() { return id; }
    public String getSellerId() { return sellerId; }
    public String getProductId() { return productId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public String getFrequency() { return frequency; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
