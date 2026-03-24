package dev.meirong.shop.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_order_log")
public class SubscriptionOrderLogEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "subscription_id", nullable = false, length = 64)
    private String subscriptionId;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "renewal_number", nullable = false)
    private int renewalNumber;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SubscriptionOrderLogEntity() {
    }

    public static SubscriptionOrderLogEntity create(String subscriptionId, String orderId,
                                                      int renewalNumber) {
        SubscriptionOrderLogEntity entity = new SubscriptionOrderLogEntity();
        entity.id = UUID.randomUUID().toString();
        entity.subscriptionId = subscriptionId;
        entity.orderId = orderId;
        entity.renewalNumber = renewalNumber;
        entity.status = "CREATED";
        return entity;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public void markPaid() { this.status = "PAID"; }
    public void markFailed() { this.status = "FAILED"; }

    public String getId() { return id; }
    public String getSubscriptionId() { return subscriptionId; }
    public String getOrderId() { return orderId; }
    public int getRenewalNumber() { return renewalNumber; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
