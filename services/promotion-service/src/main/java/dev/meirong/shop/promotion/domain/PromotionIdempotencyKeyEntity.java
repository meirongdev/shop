package dev.meirong.shop.promotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "promotion_idempotency_key")
public class PromotionIdempotencyKeyEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PromotionIdempotencyKeyEntity() {
    }

    public PromotionIdempotencyKeyEntity(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
