package dev.meirong.shop.order.domain;

public final class OrderStatus {

    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String PAID = "PAID";
    public static final String PROCESSING = "PROCESSING";
    public static final String SHIPPED = "SHIPPED";
    public static final String DELIVERED = "DELIVERED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String REFUND_REQUESTED = "REFUND_REQUESTED";
    public static final String REFUND_APPROVED = "REFUND_APPROVED";
    public static final String REFUND_REJECTED = "REFUND_REJECTED";

    private OrderStatus() {
    }
}
