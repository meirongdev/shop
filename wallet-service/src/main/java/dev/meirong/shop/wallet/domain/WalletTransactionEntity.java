package dev.meirong.shop.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_transaction")
public class WalletTransactionEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "player_id", nullable = false, length = 64)
    private String buyerId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "provider_reference", nullable = false, length = 128)
    private String providerReference;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @Column(name = "reference_type", length = 32)
    private String referenceType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WalletTransactionEntity() {
    }

    public WalletTransactionEntity(String buyerId,
                                   String type,
                                   BigDecimal amount,
                                   String currency,
                                   String status,
                                   String providerReference) {
        this.id = UUID.randomUUID().toString();
        this.buyerId = buyerId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.providerReference = providerReference;
    }

    public WalletTransactionEntity(String buyerId,
                                   String type,
                                   BigDecimal amount,
                                   String currency,
                                   String status,
                                   String providerReference,
                                   String referenceId,
                                   String referenceType) {
        this(buyerId, type, amount, currency, status, providerReference);
        this.referenceId = referenceId;
        this.referenceType = referenceType;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getBuyerId() { return buyerId; }
    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public String getProviderReference() { return providerReference; }
    public String getReferenceId() { return referenceId; }
    public String getReferenceType() { return referenceType; }
    public Instant getCreatedAt() { return createdAt; }
}
