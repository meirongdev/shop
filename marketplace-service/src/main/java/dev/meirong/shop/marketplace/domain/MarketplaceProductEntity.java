package dev.meirong.shop.marketplace.domain;

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
@Table(name = "marketplace_product")
public class MarketplaceProductEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "seller_id", nullable = false, length = 64)
    private String sellerId;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 512)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer inventory;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "category_id", length = 36)
    private String categoryId;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected MarketplaceProductEntity() {
    }

    public MarketplaceProductEntity(String sellerId,
                                    String sku,
                                    String name,
                                    String description,
                                    BigDecimal price,
                                    Integer inventory,
                                    boolean published) {
        this.id = UUID.randomUUID().toString();
        this.sellerId = sellerId;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.inventory = inventory;
        this.published = published;
        this.status = published ? "PUBLISHED" : "DRAFT";
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void update(String sellerId,
                       String sku,
                       String name,
                       String description,
                       BigDecimal price,
                       Integer inventory,
                       boolean published) {
        this.sellerId = sellerId;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.price = price;
        this.inventory = inventory;
        this.published = published;
        this.status = published ? "PUBLISHED" : "DRAFT";
    }

    public boolean deductInventory(int quantity) {
        if (this.inventory < quantity) return false;
        this.inventory -= quantity;
        return true;
    }

    public void restoreInventory(int quantity) {
        this.inventory += quantity;
    }

    public String getId() { return id; }
    public String getSellerId() { return sellerId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public Integer getInventory() { return inventory; }
    public boolean isPublished() { return published; }
    public String getCategoryId() { return categoryId; }
    public String getImageUrl() { return imageUrl; }
    public String getStatus() { return status; }
    public int getReviewCount() { return reviewCount; }
    public BigDecimal getAvgRating() { return avgRating; }

    public void updateReviewStats(int count, BigDecimal avgRating) {
        this.reviewCount = count;
        this.avgRating = avgRating;
    }
}
