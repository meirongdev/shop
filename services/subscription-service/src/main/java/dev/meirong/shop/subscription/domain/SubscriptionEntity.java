package dev.meirong.shop.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "subscription")
public class SubscriptionEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "buyer_id", nullable = false, length = 64)
    private String buyerId;

    @Column(name = "plan_id", nullable = false, length = 64)
    private String planId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "next_renewal_at")
    private Instant nextRenewalAt;

    @Column(name = "last_order_id", length = 64)
    private String lastOrderId;

    @Column(name = "total_renewals", nullable = false)
    private int totalRenewals;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SubscriptionEntity() {
    }

    public static SubscriptionEntity create(String buyerId, String planId,
                                              int quantity, String frequency) {
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.id = UUID.randomUUID().toString();
        entity.buyerId = buyerId;
        entity.planId = planId;
        entity.status = STATUS_ACTIVE;
        entity.quantity = quantity;
        entity.totalRenewals = 0;
        entity.nextRenewalAt = computeNextRenewal(Instant.now(), frequency);
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

    public void recordRenewal(String orderId, String frequency) {
        this.lastOrderId = orderId;
        this.totalRenewals++;
        this.nextRenewalAt = computeNextRenewal(Instant.now(), frequency);
    }

    public void pause() {
        if (STATUS_ACTIVE.equals(this.status)) {
            this.status = STATUS_PAUSED;
        }
    }

    public void resume(String frequency) {
        if (STATUS_PAUSED.equals(this.status)) {
            this.status = STATUS_ACTIVE;
            this.nextRenewalAt = computeNextRenewal(Instant.now(), frequency);
        }
    }

    public void cancel() {
        this.status = STATUS_CANCELLED;
        this.nextRenewalAt = null;
    }

    public boolean isDueForRenewal() {
        return STATUS_ACTIVE.equals(this.status)
                && this.nextRenewalAt != null
                && Instant.now().isAfter(this.nextRenewalAt);
    }

    static Instant computeNextRenewal(Instant from, String frequency) {
        return switch (frequency) {
            case "WEEKLY" -> from.plus(7, ChronoUnit.DAYS);
            case "BIWEEKLY" -> from.plus(14, ChronoUnit.DAYS);
            case "MONTHLY" -> from.plus(30, ChronoUnit.DAYS);
            case "QUARTERLY" -> from.plus(90, ChronoUnit.DAYS);
            default -> from.plus(30, ChronoUnit.DAYS);
        };
    }

    public String getId() { return id; }
    public String getBuyerId() { return buyerId; }
    public String getPlanId() { return planId; }
    public String getStatus() { return status; }
    public int getQuantity() { return quantity; }
    public Instant getNextRenewalAt() { return nextRenewalAt; }
    public String getLastOrderId() { return lastOrderId; }
    public int getTotalRenewals() { return totalRenewals; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
