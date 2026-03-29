package dev.meirong.shop.loyalty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loyalty_transaction")
public class LoyaltyTransactionEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "buyer_id", nullable = false, length = 64)
    private String buyerId;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @Column(length = 256)
    private String remark;

    @Column(name = "expire_at")
    private LocalDate expireAt;

    @Column(nullable = false)
    private boolean expired;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoyaltyTransactionEntity() {
    }

    public LoyaltyTransactionEntity(String buyerId, String type, String source,
                                    long amount, long balanceAfter, String referenceId, String remark) {
        this.id = UUID.randomUUID().toString();
        this.buyerId = buyerId;
        this.type = type;
        this.source = source;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.referenceId = referenceId;
        this.remark = remark;
        // Points earned this year expire on March 31 of next year
        if ("EARN".equals(type)) {
            this.expireAt = LocalDate.of(LocalDate.now().getYear() + 1, 3, 31);
        }
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getBuyerId() { return buyerId; }
    public String getType() { return type; }
    public String getSource() { return source; }
    public long getAmount() { return amount; }
    public long getBalanceAfter() { return balanceAfter; }
    public String getReferenceId() { return referenceId; }
    public String getRemark() { return remark; }
    public LocalDate getExpireAt() { return expireAt; }
    public boolean isExpired() { return expired; }
    public void markExpired() { this.expired = true; }
    public Instant getCreatedAt() { return createdAt; }
}
