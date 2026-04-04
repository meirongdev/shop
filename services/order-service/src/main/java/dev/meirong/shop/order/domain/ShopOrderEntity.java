package dev.meirong.shop.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Entity
@Table(name = "shop_order")
public class ShopOrderEntity {

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 100_000_000L);

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "order_no", nullable = false, length = 32, unique = true)
    private String orderNo;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "order_token", length = 64, unique = true)
    private String orderToken;

    @Column(name = "guest_email", length = 256)
    private String guestEmail;

    @Column(name = "buyer_id", nullable = false, length = 64)
    private String buyerId;

    @Column(name = "seller_id", nullable = false, length = 64)
    private String sellerId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "coupon_id", length = 36)
    private String couponId;

    @Column(name = "coupon_code", length = 64)
    private String couponCode;

    @Column(name = "cancel_reason", length = 256)
    private String cancelReason;

    @Column(name = "payment_transaction_id", length = 36)
    private String paymentTransactionId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShopOrderEntity() {
    }

    public ShopOrderEntity(String buyerId, String sellerId, BigDecimal subtotal, BigDecimal discountAmount,
                           BigDecimal totalAmount, String couponId, String couponCode, String paymentTransactionId) {
        this.id = UUID.randomUUID().toString();
        this.orderNo = generateOrderNo();
        this.type = "STANDARD";
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.subtotal = subtotal;
        this.discountAmount = discountAmount;
        this.totalAmount = totalAmount;
        this.couponId = couponId;
        this.couponCode = couponCode;
        this.paymentTransactionId = paymentTransactionId;
    }

    public static ShopOrderEntity createGuestOrder(String guestSessionId, String guestEmail,
                                                     String sellerId, BigDecimal subtotal,
                                                     BigDecimal totalAmount) {
        ShopOrderEntity order = new ShopOrderEntity(guestSessionId, sellerId, subtotal,
                BigDecimal.ZERO, totalAmount, null, null, null);
        order.type = "GUEST";
        order.orderToken = UUID.randomUUID().toString().replace("-", "");
        order.guestEmail = guestEmail;
        return order;
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

    private static String generateOrderNo() {
        return "ORD-" + String.format("%012d", SEQ.incrementAndGet());
    }

    // --- State Machine Transitions ---

    public void markPaid(String paymentTransactionId) {
        assertStatus(OrderStatus.PENDING_PAYMENT, "pay");
        this.status = OrderStatus.PAID;
        this.paymentTransactionId = paymentTransactionId;
        this.paidAt = Instant.now();
    }

    public void markProcessing() {
        assertStatus(OrderStatus.PAID, "process");
        this.status = OrderStatus.PROCESSING;
    }

    public void markShipped() {
        assertStatus(OrderStatus.PROCESSING, "ship");
        this.status = OrderStatus.SHIPPED;
        this.shippedAt = Instant.now();
    }

    public void markDelivered() {
        assertStatus(OrderStatus.SHIPPED, "deliver");
        this.status = OrderStatus.DELIVERED;
        this.deliveredAt = Instant.now();
    }

    public void markCompleted() {
        assertStatus(OrderStatus.DELIVERED, "complete");
        this.status = OrderStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markCancelled(String reason) {
        if (!OrderStatus.PENDING_PAYMENT.equals(this.status) && !OrderStatus.PAID.equals(this.status)) {
            throw new IllegalStateException("Cannot cancel order in status: " + this.status);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        this.cancelledAt = Instant.now();
    }

    public void markRefundRequested() {
        assertStatus(OrderStatus.PROCESSING, "request refund");
        this.status = OrderStatus.REFUND_REQUESTED;
    }

    public void markRefundApproved() {
        assertStatus(OrderStatus.REFUND_REQUESTED, "approve refund");
        this.status = OrderStatus.REFUND_APPROVED;
    }

    public void markRefundRejected() {
        assertStatus(OrderStatus.REFUND_REQUESTED, "reject refund");
        this.status = OrderStatus.REFUND_REJECTED;
    }

    private void assertStatus(String expected, String action) {
        if (!expected.equals(this.status)) {
            throw new IllegalStateException(
                    "Cannot " + action + " order in status " + this.status + " (expected " + expected + ")");
        }
    }

    // --- Getters ---

    public String getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public String getType() { return type; }
    public String getOrderToken() { return orderToken; }
    public String getGuestEmail() { return guestEmail; }
    public String getBuyerId() { return buyerId; }
    public String getSellerId() { return sellerId; }
    public String getStatus() { return status; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCouponId() { return couponId; }
    public String getCouponCode() { return couponCode; }
    public String getCancelReason() { return cancelReason; }
    public String getPaymentTransactionId() { return paymentTransactionId; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getShippedAt() { return shippedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
