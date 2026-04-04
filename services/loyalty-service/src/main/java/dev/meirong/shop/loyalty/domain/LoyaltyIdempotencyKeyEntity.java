package dev.meirong.shop.loyalty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * DB-backed idempotency key store for loyalty-service Kafka consumers.
 * Used by {@link dev.meirong.shop.common.idempotency.DbIdempotencyGuard} via
 * {@link LoyaltyIdempotencyKeyRepository}.
 */
@Entity
@Table(name = "loyalty_idempotency_key")
public class LoyaltyIdempotencyKeyEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoyaltyIdempotencyKeyEntity() {}

    public LoyaltyIdempotencyKeyEntity(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    public String getIdempotencyKey() { return idempotencyKey; }
}
