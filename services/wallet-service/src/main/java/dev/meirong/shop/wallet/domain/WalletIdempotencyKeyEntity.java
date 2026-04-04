package dev.meirong.shop.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "wallet_idempotency_key")
public class WalletIdempotencyKeyEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WalletIdempotencyKeyEntity() {
    }

    public WalletIdempotencyKeyEntity(String idempotencyKey, String transactionId) {
        this.idempotencyKey = idempotencyKey;
        this.transactionId = transactionId;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
