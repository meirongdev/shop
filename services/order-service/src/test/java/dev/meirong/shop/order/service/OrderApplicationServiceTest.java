package dev.meirong.shop.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.contracts.api.OrderApi;
import dev.meirong.shop.order.domain.OrderItemEntity;
import dev.meirong.shop.order.domain.OrderItemRepository;
import dev.meirong.shop.order.domain.OrderOutboxEventEntity;
import dev.meirong.shop.order.domain.OrderOutboxEventRepository;
import dev.meirong.shop.order.domain.OrderRefundRepository;
import dev.meirong.shop.order.domain.OrderShipmentRepository;
import dev.meirong.shop.order.domain.OrderStatus;
import dev.meirong.shop.order.domain.ShopOrderEntity;
import dev.meirong.shop.order.domain.ShopOrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    @Mock
    private ShopOrderRepository orderRepository;

    @Mock
    private OrderItemRepository itemRepository;

    @Mock
    private OrderShipmentRepository shipmentRepository;

    @Mock
    private OrderRefundRepository refundRepository;

    @Mock
    private OrderOutboxEventRepository outboxRepository;

    private SimpleMeterRegistry meterRegistry;
    private OrderApplicationService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new OrderApplicationService(
                orderRepository,
                itemRepository,
                shipmentRepository,
                refundRepository,
                outboxRepository,
                new ObjectMapper().findAndRegisterModules(),
                meterRegistry);
    }

    @Test
    void confirmPayment_recordsSuccessMetric() {
        ShopOrderEntity order = new ShopOrderEntity(
                "buyer-1",
                "seller-1",
                BigDecimal.valueOf(100),
                BigDecimal.ZERO,
                BigDecimal.valueOf(100),
                null,
                null,
                null);
        OrderItemEntity item = new OrderItemEntity(
                order.getId(),
                "prod-1",
                "Alpha Serum",
                BigDecimal.valueOf(100),
                1,
                BigDecimal.valueOf(100));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(itemRepository.findByOrderId(order.getId())).thenReturn(List.of(item));

        OrderApi.OrderResponse response =
                service.confirmPayment(new OrderApi.PaymentConfirmRequest(order.getId(), "payment-1"));

        assertThat(response.status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(meterRegistry.get("shop_payment_confirm_total")
                .tag("service", "order-service")
                .tag("operation", "confirm")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        verify(outboxRepository).save(any(OrderOutboxEventEntity.class));
    }

    @Test
    void confirmPayment_recordsRejectedMetricForCancelledOrder() {
        ShopOrderEntity order = new ShopOrderEntity(
                "buyer-1",
                "seller-1",
                BigDecimal.valueOf(100),
                BigDecimal.ZERO,
                BigDecimal.valueOf(100),
                null,
                null,
                null);
        order.markCancelled("timeout");
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                service.confirmPayment(new OrderApi.PaymentConfirmRequest(order.getId(), "payment-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Order already cancelled");

        assertThat(meterRegistry.get("shop_payment_confirm_total")
                .tag("service", "order-service")
                .tag("operation", "confirm")
                .tag("result", "rejected")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
