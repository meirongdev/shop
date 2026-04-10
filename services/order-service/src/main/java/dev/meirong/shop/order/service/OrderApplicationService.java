package dev.meirong.shop.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.order.OrderApi;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.OrderEventData;
import dev.meirong.shop.order.domain.OrderItemEntity;
import dev.meirong.shop.order.domain.OrderItemRepository;
import dev.meirong.shop.order.domain.OrderOutboxEventEntity;
import dev.meirong.shop.order.domain.OrderOutboxEventRepository;
import dev.meirong.shop.order.domain.OrderRefundEntity;
import dev.meirong.shop.order.domain.OrderRefundRepository;
import dev.meirong.shop.order.domain.OrderShipmentEntity;
import dev.meirong.shop.order.domain.OrderShipmentRepository;
import dev.meirong.shop.order.domain.OrderStatus;
import dev.meirong.shop.order.domain.ShopOrderEntity;
import dev.meirong.shop.order.domain.ShopOrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderApplicationService.class);
    private static final String ORDER_TOPIC = "order.events.v1";

    private final ShopOrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final OrderShipmentRepository shipmentRepository;
    private final OrderRefundRepository refundRepository;
    private final OrderOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public OrderApplicationService(ShopOrderRepository orderRepository,
                                   OrderItemRepository itemRepository,
                                   OrderShipmentRepository shipmentRepository,
                                   OrderRefundRepository refundRepository,
                                   OrderOutboxEventRepository outboxRepository,
                                   ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.shipmentRepository = shipmentRepository;
        this.refundRepository = refundRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public OrderApi.OrderResponse createOrder(OrderApi.CreateOrderRequest request) {
        ShopOrderEntity order = new ShopOrderEntity(
                request.buyerId(), request.sellerId(), request.subtotal(),
                request.discountAmount(), request.totalAmount(),
                request.couponId(), request.couponCode(), request.paymentTransactionId());
        orderRepository.save(order);

        List<OrderItemEntity> items = request.items().stream()
                .map(item -> new OrderItemEntity(order.getId(), item.productId(), item.productName(),
                        item.productPrice(), item.quantity(), item.lineTotal()))
                .toList();
        itemRepository.saveAll(items);

        publishOutboxEvent(order, "order.created.v1", items);
        Counter.builder("shop_order_created_total")
                .description("Total number of orders created")
                .tag("service", "order-service")
                .register(meterRegistry).increment();
        return toResponse(order, items);
    }

    @Transactional
    public OrderApi.OrderResponse confirmPayment(OrderApi.PaymentConfirmRequest request) {
        String result = "success";
        try {
            ShopOrderEntity order = orderRepository.findById(request.orderId())
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Order not found: " + request.orderId()));

            if (OrderStatus.PAID.equals(order.getStatus())) {
                result = "idempotent";
                log.info("Payment already confirmed for order {}, idempotent return", order.getId());
                return toResponse(order, itemRepository.findByOrderId(order.getId()));
            }
            if (OrderStatus.CANCELLED.equals(order.getStatus())) {
                result = "rejected";
                throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                        "Order already cancelled (timeout): " + order.getId());
            }

            order.markPaid(request.paymentTransactionId());
            order.markProcessing();
            orderRepository.save(order);

            List<OrderItemEntity> items = itemRepository.findByOrderId(order.getId());
            publishOutboxEvent(order, "order.paid.v1", items);
            return toResponse(order, items);
        } catch (RuntimeException exception) {
            if ("success".equals(result)) {
                result = "failure";
            }
            throw exception;
        } finally {
            paymentConfirmCounter(result).increment();
        }
    }

    @Transactional
    public OrderApi.OrderResponse shipOrder(OrderApi.ShipOrderRequest request) {
        ShopOrderEntity order = findOrder(request.orderId());
        order.markShipped();
        orderRepository.save(order);

        OrderShipmentEntity shipment = new OrderShipmentEntity(order.getId(), request.carrier(), request.trackingNo());
        shipmentRepository.save(shipment);

        List<OrderItemEntity> items = itemRepository.findByOrderId(order.getId());
        publishOutboxEvent(order, "order.shipped.v1", items);
        return toResponse(order, items);
    }

    @Transactional
    public OrderApi.OrderResponse confirmDelivery(String orderId) {
        ShopOrderEntity order = findOrder(orderId);
        order.markDelivered();
        orderRepository.save(order);

        shipmentRepository.findByOrderId(orderId).ifPresent(s -> {
            s.markDelivered();
            shipmentRepository.save(s);
        });

        List<OrderItemEntity> items = itemRepository.findByOrderId(orderId);
        publishOutboxEvent(order, "order.delivered.v1", items);
        return toResponse(order, items);
    }

    @Transactional
    public OrderApi.OrderResponse confirmReceipt(String orderId) {
        ShopOrderEntity order = findOrder(orderId);
        order.markCompleted();
        orderRepository.save(order);

        List<OrderItemEntity> items = itemRepository.findByOrderId(orderId);
        publishOutboxEvent(order, "order.completed.v1", items);
        return toResponse(order, items);
    }

    @Transactional
    public OrderApi.OrderResponse cancelOrder(OrderApi.CancelOrderRequest request) {
        ShopOrderEntity order = findOrder(request.orderId());
        order.markCancelled(request.reason() != null ? request.reason() : "User cancelled");
        orderRepository.save(order);

        List<OrderItemEntity> items = itemRepository.findByOrderId(order.getId());
        publishOutboxEvent(order, "order.cancelled.v1", items);
        return toResponse(order, items);
    }

    @Transactional
    public OrderApi.RefundResponse requestRefund(OrderApi.RefundRequest request) {
        ShopOrderEntity order = findOrder(request.orderId());
        order.markRefundRequested();
        orderRepository.save(order);

        OrderRefundEntity refund = new OrderRefundEntity(
                order.getId(), request.reason(), request.amount(), request.requesterId());
        refundRepository.save(refund);

        publishOutboxEvent(order, "order.refund_requested.v1", itemRepository.findByOrderId(order.getId()));
        return toRefundResponse(refund);
    }

    @Transactional
    public OrderApi.RefundResponse reviewRefund(OrderApi.RefundReviewRequest request) {
        OrderRefundEntity refund = refundRepository.findById(request.refundId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Refund not found: " + request.refundId()));
        ShopOrderEntity order = findOrder(refund.getOrderId());

        if (request.approved()) {
            refund.approve(request.reviewerId());
            order.markRefundApproved();
            publishOutboxEvent(order, "order.refunded.v1", itemRepository.findByOrderId(order.getId()));
        } else {
            refund.reject(request.reviewerId(), request.remark());
            order.markRefundRejected();
        }

        refundRepository.save(refund);
        orderRepository.save(order);
        return toRefundResponse(refund);
    }

    @Transactional(readOnly = true)
    public List<OrderApi.OrderResponse> listOrders(OrderApi.ListOrdersRequest request) {
        List<ShopOrderEntity> orders = "seller".equals(request.role())
                ? orderRepository.findBySellerIdOrderByCreatedAtDesc(request.buyerId())
                : orderRepository.findByBuyerIdOrderByCreatedAtDesc(request.buyerId());
        if (orders.isEmpty()) return List.of();
        List<String> orderIds = orders.stream().map(ShopOrderEntity::getId).toList();
        Map<String, List<OrderItemEntity>> itemsByOrder = itemRepository.findByOrderIdIn(orderIds)
                .stream().collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        return orders.stream()
                .map(order -> toResponse(order, itemsByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderApi.OrderResponse getOrder(String orderId) {
        ShopOrderEntity order = findOrder(orderId);
        List<OrderItemEntity> items = itemRepository.findByOrderId(orderId);
        return toResponse(order, items);
    }

    @Transactional
    public OrderApi.OrderResponse guestCheckout(OrderApi.GuestCheckoutRequest request, String guestSessionId) {
        BigDecimal lineTotal = request.productPrice().multiply(BigDecimal.valueOf(request.quantity()));
        ShopOrderEntity order = ShopOrderEntity.createGuestOrder(
                guestSessionId, request.guestEmail(), request.sellerId(), lineTotal, lineTotal);
        orderRepository.save(order);

        List<OrderItemEntity> items = List.of(new OrderItemEntity(
                order.getId(), request.productId(), request.productName(),
                request.productPrice(), request.quantity(), lineTotal));
        itemRepository.saveAll(items);

        publishOutboxEvent(order, "order.created.v1", items);
        return toResponse(order, items);
    }

    @Transactional(readOnly = true)
    public OrderApi.OrderResponse trackOrder(String orderToken) {
        ShopOrderEntity order = orderRepository.findByOrderToken(orderToken)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Order not found for token"));
        List<OrderItemEntity> items = itemRepository.findByOrderId(order.getId());
        return toResponse(order, items);
    }

    // --- Scheduled tasks call these ---

    @Transactional
    public void cancelExpiredOrders(Instant threshold) {
        List<ShopOrderEntity> expired = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING_PAYMENT, threshold);
        for (ShopOrderEntity order : expired) {
            order.markCancelled("Payment timeout - auto cancelled");
            orderRepository.save(order);
            publishOutboxEvent(order, "order.cancelled.v1", itemRepository.findByOrderId(order.getId()));
            log.info("Auto-cancelled expired order: {}", order.getId());
        }
    }

    @Transactional
    public void autoCompleteDeliveredOrders(Instant threshold) {
        List<ShopOrderEntity> delivered = orderRepository.findByStatusAndDeliveredAtBefore(
                OrderStatus.DELIVERED, threshold);
        for (ShopOrderEntity order : delivered) {
            order.markCompleted();
            orderRepository.save(order);
            publishOutboxEvent(order, "order.completed.v1", itemRepository.findByOrderId(order.getId()));
            log.info("Auto-completed delivered order: {}", order.getId());
        }
    }

    // --- Helpers ---

    private ShopOrderEntity findOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Order not found: " + orderId));
    }

    private void publishOutboxEvent(ShopOrderEntity order, String eventType, List<OrderItemEntity> items) {
        try {
            OrderEventData data = new OrderEventData(
                    order.getId(), order.getOrderNo(), order.getBuyerId(), order.getSellerId(),
                    order.getStatus(), order.getTotalAmount(),
                    items.stream().map(i -> new OrderEventData.OrderItemSummary(
                            i.getProductId(), i.getProductName(), i.getQuantity(), i.getLineTotal())).toList());
            EventEnvelope<OrderEventData> envelope = new EventEnvelope<>(
                    UUID.randomUUID().toString(), "order-service", eventType, Instant.now(), data);
            String payload = objectMapper.writeValueAsString(envelope);
            outboxRepository.save(new OrderOutboxEventEntity(order.getId(), ORDER_TOPIC, eventType, payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event for order {}", order.getId(), e);
        }
    }

    private OrderApi.OrderResponse toResponse(ShopOrderEntity order, List<OrderItemEntity> items) {
        return new OrderApi.OrderResponse(
                UUID.fromString(order.getId()),
                order.getOrderNo(),
                order.getType(),
                order.getOrderToken(),
                order.getBuyerId(),
                order.getSellerId(),
                order.getStatus(),
                order.getSubtotal(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getCouponId(),
                order.getCouponCode(),
                order.getPaymentTransactionId(),
                items.stream().map(this::toItemResponse).toList(),
                order.getPaidAt(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private OrderApi.OrderItemResponse toItemResponse(OrderItemEntity item) {
        return new OrderApi.OrderItemResponse(
                UUID.fromString(item.getId()),
                item.getOrderId(),
                item.getProductId(),
                item.getProductName(),
                item.getProductPrice(),
                item.getQuantity(),
                item.getLineTotal());
    }

    private OrderApi.RefundResponse toRefundResponse(OrderRefundEntity refund) {
        return new OrderApi.RefundResponse(
                UUID.fromString(refund.getId()),
                refund.getOrderId(),
                refund.getRefundNo(),
                refund.getReason(),
                refund.getAmount(),
                refund.getStatus(),
                refund.getRequester(),
                refund.getReviewer(),
                refund.getReviewRemark(),
                refund.getCompletedAt(),
                refund.getCreatedAt());
    }

    private Counter paymentConfirmCounter(String result) {
        return Counter.builder("shop_payment_confirm_total")
                .tag("service", "order-service")
                .tag("operation", "confirm")
                .tag("result", result)
                .register(meterRegistry);
    }
}
