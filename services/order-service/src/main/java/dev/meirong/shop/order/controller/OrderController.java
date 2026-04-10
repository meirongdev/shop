package dev.meirong.shop.order.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.order.OrderApi;
import dev.meirong.shop.order.service.CartService;
import dev.meirong.shop.order.service.OrderApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(OrderApi.BASE_PATH)
public class OrderController {

    private final CartService cartService;
    private final OrderApplicationService orderService;

    public OrderController(CartService cartService, OrderApplicationService orderService) {
        this.cartService = cartService;
        this.orderService = orderService;
    }

    // --- Cart ---

    @PostMapping("/cart/list")
    public ApiResponse<OrderApi.CartView> listCart(@Valid @RequestBody OrderApi.ListCartRequest request) {
        return ApiResponse.success(cartService.listCart(request.buyerId()));
    }

    @PostMapping("/cart/add")
    public ApiResponse<OrderApi.CartItemResponse> addToCart(@Valid @RequestBody OrderApi.AddToCartRequest request) {
        return ApiResponse.success(cartService.addToCart(request));
    }

    @PostMapping("/cart/update")
    public ApiResponse<OrderApi.CartItemResponse> updateCart(@Valid @RequestBody OrderApi.UpdateCartRequest request) {
        return ApiResponse.success(cartService.updateCart(request));
    }

    @PostMapping("/cart/remove")
    public ApiResponse<Void> removeFromCart(@Valid @RequestBody OrderApi.RemoveFromCartRequest request) {
        cartService.removeFromCart(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/cart/clear")
    public ApiResponse<Void> clearCart(@Valid @RequestBody OrderApi.ClearCartRequest request) {
        cartService.clearCart(request.buyerId());
        return ApiResponse.success(null);
    }

    // --- Orders ---

    @PostMapping("/checkout/create")
    public ApiResponse<OrderApi.OrderResponse> createOrder(@Valid @RequestBody OrderApi.CreateOrderRequest request) {
        return ApiResponse.success(orderService.createOrder(request));
    }

    @PostMapping("/order/list")
    public ApiResponse<List<OrderApi.OrderResponse>> listOrders(@Valid @RequestBody OrderApi.ListOrdersRequest request) {
        return ApiResponse.success(orderService.listOrders(request));
    }

    @PostMapping("/order/get")
    public ApiResponse<OrderApi.OrderResponse> getOrder(@Valid @RequestBody OrderApi.GetOrderRequest request) {
        return ApiResponse.success(orderService.getOrder(request.orderId()));
    }

    @PostMapping("/order/cancel")
    public ApiResponse<OrderApi.OrderResponse> cancelOrder(@Valid @RequestBody OrderApi.CancelOrderRequest request) {
        return ApiResponse.success(orderService.cancelOrder(request));
    }

    @PostMapping("/order/ship")
    public ApiResponse<OrderApi.OrderResponse> shipOrder(@Valid @RequestBody OrderApi.ShipOrderRequest request) {
        return ApiResponse.success(orderService.shipOrder(request));
    }

    @PostMapping("/order/deliver")
    public ApiResponse<OrderApi.OrderResponse> deliverOrder(@Valid @RequestBody OrderApi.ConfirmOrderRequest request) {
        return ApiResponse.success(orderService.confirmDelivery(request.orderId()));
    }

    @PostMapping("/order/confirm")
    public ApiResponse<OrderApi.OrderResponse> confirmReceipt(@Valid @RequestBody OrderApi.ConfirmOrderRequest request) {
        return ApiResponse.success(orderService.confirmReceipt(request.orderId()));
    }

    @PostMapping("/order/refund/request")
    public ApiResponse<OrderApi.RefundResponse> requestRefund(@Valid @RequestBody OrderApi.RefundRequest request) {
        return ApiResponse.success(orderService.requestRefund(request));
    }

    @PostMapping("/order/refund/review")
    public ApiResponse<OrderApi.RefundResponse> reviewRefund(@Valid @RequestBody OrderApi.RefundReviewRequest request) {
        return ApiResponse.success(orderService.reviewRefund(request));
    }

    // --- Guest ---

    @PostMapping("/checkout/guest")
    public ApiResponse<OrderApi.OrderResponse> guestCheckout(@Valid @RequestBody OrderApi.GuestCheckoutRequest request) {
        String guestSessionId = "guest-" + UUID.randomUUID().toString().substring(0, 8);
        return ApiResponse.success(orderService.guestCheckout(request, guestSessionId));
    }

    @GetMapping("/order/track")
    public ApiResponse<OrderApi.OrderResponse> trackOrder(@RequestParam String token) {
        return ApiResponse.success(orderService.trackOrder(token));
    }
}
