package dev.meirong.shop.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_account")
public class WalletAccountEntity {

    @Id
    @Column(name = "buyer_id", nullable = false, length = 64)
    private String buyerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WalletAccountEntity() {
    }

    public WalletAccountEntity(String buyerId, BigDecimal balance) {
        this.buyerId = buyerId;
        this.balance = balance;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public String getBuyerId() {
        return buyerId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
