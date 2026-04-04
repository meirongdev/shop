package dev.meirong.shop.order;

import dev.meirong.shop.order.domain.OrderStatus;
import dev.meirong.shop.order.domain.ShopOrderEntity;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuestShoppingTest {

    @Test
    void createGuestOrder_setsTypeAndToken() {
        ShopOrderEntity order = ShopOrderEntity.createGuestOrder(
                "guest-abc123", "guest@example.com", "seller1",
                BigDecimal.valueOf(50), BigDecimal.valueOf(50));

        assertEquals("GUEST", order.getType());
        assertNotNull(order.getOrderToken());
        assertEquals(32, order.getOrderToken().length());
        assertEquals("guest@example.com", order.getGuestEmail());
        assertEquals("guest-abc123", order.getBuyerId());
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        assertEquals(BigDecimal.ZERO, order.getDiscountAmount());
    }

    @Test
    void standardOrder_hasNoToken() {
        ShopOrderEntity order = new ShopOrderEntity("buyer1", "seller1",
                BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.valueOf(100),
                null, null, null);

        assertEquals("STANDARD", order.getType());
        assertNull(order.getOrderToken());
        assertNull(order.getGuestEmail());
    }

    @Test
    void guestOrder_fullLifecycle() {
        ShopOrderEntity order = ShopOrderEntity.createGuestOrder(
                "guest-xyz", "buyer@test.com", "seller1",
                BigDecimal.valueOf(25), BigDecimal.valueOf(25));

        order.markPaid("payment-001");
        assertEquals(OrderStatus.PAID, order.getStatus());

        order.markProcessing();
        assertEquals(OrderStatus.PROCESSING, order.getStatus());

        order.markShipped();
        assertEquals(OrderStatus.SHIPPED, order.getStatus());

        order.markDelivered();
        assertEquals(OrderStatus.DELIVERED, order.getStatus());

        order.markCompleted();
        assertEquals(OrderStatus.COMPLETED, order.getStatus());

        // Token remains throughout lifecycle
        assertNotNull(order.getOrderToken());
        assertEquals("GUEST", order.getType());
    }

    @Test
    void guestOrder_canBeCancelled() {
        ShopOrderEntity order = ShopOrderEntity.createGuestOrder(
                "guest-cancel", "cancel@test.com", "seller1",
                BigDecimal.valueOf(30), BigDecimal.valueOf(30));

        order.markCancelled("Guest changed mind");
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void guestOrderToken_isUnique() {
        ShopOrderEntity order1 = ShopOrderEntity.createGuestOrder(
                "guest-1", "a@test.com", "seller1",
                BigDecimal.valueOf(10), BigDecimal.valueOf(10));
        ShopOrderEntity order2 = ShopOrderEntity.createGuestOrder(
                "guest-2", "b@test.com", "seller1",
                BigDecimal.valueOf(20), BigDecimal.valueOf(20));

        assertNotEquals(order1.getOrderToken(), order2.getOrderToken());
    }
}
