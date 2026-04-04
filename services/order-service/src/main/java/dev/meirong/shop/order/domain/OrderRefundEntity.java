package dev.meirong.shop.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Entity
@Table(name = "order_refund")
public class OrderRefundEntity {

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 100_000_000L);

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "refund_no", nullable = false, length = 32, unique = true)
    private String refundNo;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 64)
    private String requester;

    @Column(length = 64)
    private String reviewer;

    @Column(name = "review_remark", length = 512)
    private String reviewRemark;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderRefundEntity() {
    }

    public OrderRefundEntity(String orderId, String reason, BigDecimal amount, String requester) {
        this.id = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.refundNo = "REF-" + String.format("%012d", SEQ.incrementAndGet());
        this.reason = reason;
        this.amount = amount;
        this.status = "PENDING";
        this.requester = requester;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public void approve(String reviewer) {
        this.status = "APPROVED";
        this.reviewer = reviewer;
        this.completedAt = Instant.now();
    }

    public void reject(String reviewer, String remark) {
        this.status = "REJECTED";
        this.reviewer = reviewer;
        this.reviewRemark = remark;
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getRefundNo() { return refundNo; }
    public String getReason() { return reason; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public String getRequester() { return requester; }
    public String getReviewer() { return reviewer; }
    public String getReviewRemark() { return reviewRemark; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
