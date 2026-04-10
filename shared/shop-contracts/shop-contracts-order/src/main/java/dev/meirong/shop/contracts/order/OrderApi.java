package dev.meirong.shop.contracts.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderApi {

    public static final String BASE_PATH = "/order/v1";
    public static final String CART_LIST = BASE_PATH + "/cart/list";
    public static final String CART_ADD = BASE_PATH + "/cart/add";
    public static final String CART_UPDATE = BASE_PATH + "/cart/update";
    public static final String CART_REMOVE = BASE_PATH + "/cart/remove";
    public static final String CART_CLEAR = BASE_PATH + "/cart/clear";
    public static final String CHECKOUT_PREVIEW = BASE_PATH + "/checkout/preview";
    public static final String CHECKOUT_CREATE = BASE_PATH + "/checkout/create";
    public static final String ORDER_LIST = BASE_PATH + "/order/list";
    public static final String ORDER_GET = BASE_PATH + "/order/get";
    public static final String ORDER_CANCEL = BASE_PATH + "/order/cancel";
    public static final String ORDER_SHIP = BASE_PATH + "/order/ship";
    public static final String ORDER_DELIVER = BASE_PATH + "/order/deliver";
    public static final String ORDER_CONFIRM = BASE_PATH + "/order/confirm";
    public static final String ORDER_REFUND_REQUEST = BASE_PATH + "/order/refund/request";
    public static final String ORDER_REFUND_REVIEW = BASE_PATH + "/order/refund/review";
    public static final String INTERNAL_PAYMENT_CONFIRM = "/internal/orders/payment-confirm";
    public static final String ORDER_TRACK = BASE_PATH + "/order/track";
    public static final String GUEST_CHECKOUT = BASE_PATH + "/checkout/guest";

    private OrderApi() {
    }

    @Schema(description = "添加购物车请求")
    public record AddToCartRequest(
            @Schema(description = "买家 ID", example = "buyer-01HX123456")
            @NotBlank String buyerId,
            @Schema(description = "商品 ID", example = "prod-01HX789012")
            @NotBlank String productId,
            @Schema(description = "商品名称", example = "iPhone 15 Pro")
            @NotBlank String productName,
            @Schema(description = "商品单价", example = "7999.00")
            @NotNull BigDecimal productPrice,
            @Schema(description = "卖家 ID", example = "seller-01HX345678")
            @NotBlank String sellerId,
            @Schema(description = "购买数量", example = "1")
            @NotNull Integer quantity) {
    }

    public record UpdateCartRequest(@NotBlank String buyerId,
                                    @NotBlank String productId,
                                    @NotNull Integer quantity) {
    }

    public record RemoveFromCartRequest(@NotBlank String buyerId,
                                        @NotBlank String productId) {
    }

    public record ClearCartRequest(@NotBlank String buyerId) {
    }

    public record ListCartRequest(@NotBlank String buyerId) {
    }

    public record CartItemResponse(UUID id,
                                   String buyerId,
                                   String productId,
                                   String productName,
                                   BigDecimal productPrice,
                                   String sellerId,
                                   int quantity,
                                   Instant createdAt) {
    }

    public record CartView(List<CartItemResponse> items, BigDecimal subtotal) {
    }

    public record CreateOrderRequest(@NotBlank String buyerId,
                                     @NotBlank String sellerId,
                                     BigDecimal subtotal,
                                     BigDecimal discountAmount,
                                     BigDecimal totalAmount,
                                     String couponId,
                                     String couponCode,
                                     String paymentTransactionId,
                                     List<CreateOrderItemRequest> items) {
    }

    public record CreateOrderItemRequest(String productId,
                                         String productName,
                                         BigDecimal productPrice,
                                         int quantity,
                                         BigDecimal lineTotal) {
    }

    public record ListOrdersRequest(@NotBlank String buyerId,
                                    String role) {
    }

    public record GetOrderRequest(@NotBlank String orderId) {
    }

    public record UpdateOrderStatusRequest(@NotBlank String orderId,
                                           @NotBlank String status) {
    }

    // --- Ship request ---
    public record ShipOrderRequest(@NotBlank String orderId,
                                   String carrier,
                                   @NotBlank String trackingNo) {
    }

    // --- Cancel request ---
    public record CancelOrderRequest(@NotBlank String orderId,
                                     String reason) {
    }

    // --- Confirm receipt request ---
    public record ConfirmOrderRequest(@NotBlank String orderId) {
    }

    // --- Refund request ---
    public record RefundRequest(@NotBlank String orderId,
                                @NotBlank String reason,
                                @NotNull BigDecimal amount,
                                @NotBlank String requesterId) {
    }

    // --- Refund review ---
    public record RefundReviewRequest(@NotBlank String refundId,
                                      @NotBlank String reviewerId,
                                      boolean approved,
                                      String remark) {
    }

    // --- Payment confirm (internal) ---
    public record PaymentConfirmRequest(@NotBlank String orderId,
                                        @NotBlank String paymentTransactionId) {
    }

    public record OrderResponse(UUID id,
                                String orderNo,
                                String type,
                                String orderToken,
                                String buyerId,
                                String sellerId,
                                String status,
                                BigDecimal subtotal,
                                BigDecimal discountAmount,
                                BigDecimal totalAmount,
                                String couponId,
                                String couponCode,
                                String paymentTransactionId,
                                List<OrderItemResponse> items,
                                Instant paidAt,
                                Instant shippedAt,
                                Instant deliveredAt,
                                Instant completedAt,
                                Instant cancelledAt,
                                Instant createdAt,
                                Instant updatedAt) {
    }

    public record GuestCheckoutRequest(@NotBlank String guestEmail,
                                        @NotBlank String productId,
                                        @NotBlank String productName,
                                        @NotNull BigDecimal productPrice,
                                        @NotBlank String sellerId,
                                        @NotNull Integer quantity) {
    }

    public record OrderItemResponse(UUID id,
                                    String orderId,
                                    String productId,
                                    String productName,
                                    BigDecimal productPrice,
                                    int quantity,
                                    BigDecimal lineTotal) {
    }

    public record RefundResponse(UUID id,
                                 String orderId,
                                 String refundNo,
                                 String reason,
                                 BigDecimal amount,
                                 String status,
                                 String requester,
                                 String reviewer,
                                 String reviewRemark,
                                 Instant completedAt,
                                 Instant createdAt) {
    }
}
