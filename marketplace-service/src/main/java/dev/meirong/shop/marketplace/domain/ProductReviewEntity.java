package dev.meirong.shop.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_review")
public class ProductReviewEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "buyer_id", nullable = false, length = 64)
    private String buyerId;

    @Column(name = "order_id", length = 36)
    private String orderId;

    @Column(nullable = false, columnDefinition = "TINYINT")
    private byte rating;

    @Column(length = 2000)
    private String content;

    @Column(columnDefinition = "JSON")
    private String images;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductReviewEntity() {}

    public ProductReviewEntity(String productId, String buyerId, String orderId,
                                int rating, String content, String images) {
        this.id = UUID.randomUUID().toString();
        this.productId = productId;
        this.buyerId = buyerId;
        this.orderId = orderId;
        this.rating = (byte) rating;
        this.content = content;
        this.images = images;
        this.status = "APPROVED";
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

    // Getters
    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getBuyerId() { return buyerId; }
    public String getOrderId() { return orderId; }
    public int getRating() { return rating; }
    public String getContent() { return content; }
    public String getImages() { return images; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) { this.status = status; }
}
