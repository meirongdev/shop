package dev.meirong.shop.order;

import dev.meirong.shop.order.domain.OrderStatus;
import dev.meirong.shop.order.domain.ShopOrderEntity;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTest {

    private ShopOrderEntity createOrder() {
        return new ShopOrderEntity("buyer1", "seller1",
                BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.valueOf(100),
                null, null, null);
    }

    @Test
    void newOrder_hasPendingPaymentStatus() {
        ShopOrderEntity order = createOrder();
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertNotNull(order.getOrderNo());
        assertTrue(order.getOrderNo().startsWith("ORD-"));
    }

    @Test
    void fullLifecycle_pendingToCompleted() {
        ShopOrderEntity order = createOrder();

        order.markPaid("txn-123");
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertNotNull(order.getPaidAt());

        order.markProcessing();
        assertEquals(OrderStatus.PROCESSING, order.getStatus());

        order.markShipped();
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertNotNull(order.getShippedAt());

        order.markDelivered();
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertNotNull(order.getDeliveredAt());

        order.markCompleted();
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertNotNull(order.getCompletedAt());
    }

    @Test
    void cancel_fromPendingPayment_succeeds() {
        ShopOrderEntity order = createOrder();
        order.markCancelled("User cancelled");
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals("User cancelled", order.getCancelReason());
        assertNotNull(order.getCancelledAt());
    }

    @Test
    void cancel_fromPaid_succeeds() {
        ShopOrderEntity order = createOrder();
        order.markPaid("txn-123");
        order.markCancelled("Changed mind");
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void cancel_fromShipped_throws() {
        ShopOrderEntity order = createOrder();
        order.markPaid("txn-123");
        order.markProcessing();
        order.markShipped();
        assertThrows(IllegalStateException.class, () -> order.markCancelled("Too late"));
    }

    @Test
    void invalidTransition_payFromPaid_throws() {
        ShopOrderEntity order = createOrder();
        order.markPaid("txn-1");
        assertThrows(IllegalStateException.class, () -> order.markPaid("txn-2"));
    }

    @Test
    void refundFlow_fromProcessing() {
        ShopOrderEntity order = createOrder();
        order.markPaid("txn-1");
        order.markProcessing();

        order.markRefundRequested();
        assertEquals(OrderStatus.REFUND_REQUESTED, order.getStatus());

        order.markRefundApproved();
        assertEquals(OrderStatus.REFUND_APPROVED, order.getStatus());
    }

    @Test
    void refundRejected_fromRefundRequested() {
        ShopOrderEntity order = createOrder();
        order.markPaid("txn-1");
        order.markProcessing();
        order.markRefundRequested();

        order.markRefundRejected();
        assertEquals(OrderStatus.REFUND_REJECTED, order.getStatus());
    }

    @Test
    void ship_fromPaid_throws() {
        ShopOrderEntity order = createOrder();
        order.markPaid("txn-1");
        // Must go through PROCESSING first
        assertThrows(IllegalStateException.class, order::markShipped);
    }
}
