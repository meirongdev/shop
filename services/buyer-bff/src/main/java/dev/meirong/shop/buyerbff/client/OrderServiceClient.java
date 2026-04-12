package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.order.OrderApi;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface OrderServiceClient {

    @PostExchange(OrderApi.CART_LIST)
    ApiResponse<OrderApi.CartView> listCart(
            @RequestBody OrderApi.ListCartRequest request);

    @PostExchange(OrderApi.CART_ADD)
    ApiResponse<OrderApi.CartItemResponse> addToCart(
            @RequestBody OrderApi.AddToCartRequest request);

    @PostExchange(OrderApi.CART_UPDATE)
    ApiResponse<OrderApi.CartItemResponse> updateCart(
            @RequestBody OrderApi.UpdateCartRequest request);

    @PostExchange(OrderApi.CART_REMOVE)
    ApiResponse<Void> removeFromCart(
            @RequestBody OrderApi.RemoveFromCartRequest request);

    @PostExchange(OrderApi.CART_CLEAR)
    ApiResponse<Void> clearCart(
            @RequestBody OrderApi.ClearCartRequest request);

    @PostExchange(OrderApi.CHECKOUT_CREATE)
    ApiResponse<OrderApi.OrderResponse> createOrder(
            @RequestBody OrderApi.CreateOrderRequest request);

    @PostExchange(OrderApi.ORDER_LIST)
    ApiResponse<List<OrderApi.OrderResponse>> listOrders(
            @RequestBody OrderApi.ListOrdersRequest request);

    @PostExchange(OrderApi.ORDER_GET)
    ApiResponse<OrderApi.OrderResponse> getOrder(
            @RequestBody OrderApi.GetOrderRequest request);

    @PostExchange(OrderApi.ORDER_CANCEL)
    ApiResponse<OrderApi.OrderResponse> cancelOrder(
            @RequestBody OrderApi.CancelOrderRequest request);

    @PostExchange(OrderApi.GUEST_CHECKOUT)
    ApiResponse<OrderApi.OrderResponse> guestCheckout(
            @RequestBody OrderApi.GuestCheckoutRequest request);

    @GetExchange(OrderApi.ORDER_TRACK)
    ApiResponse<OrderApi.OrderResponse> trackOrder(
            @RequestParam("token") String token);
}
