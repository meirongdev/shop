package dev.meirong.shop.sellerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.order.OrderApi;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface OrderServiceClient {

    @PostExchange(OrderApi.ORDER_LIST)
    ApiResponse<List<OrderApi.OrderResponse>> listOrders(
            @RequestBody OrderApi.ListOrdersRequest request);

    @PostExchange(OrderApi.ORDER_GET)
    ApiResponse<OrderApi.OrderResponse> getOrder(
            @RequestBody OrderApi.GetOrderRequest request);

    @PostExchange(OrderApi.ORDER_SHIP)
    ApiResponse<OrderApi.OrderResponse> shipOrder(
            @RequestBody OrderApi.ShipOrderRequest request);

    @PostExchange(OrderApi.ORDER_DELIVER)
    ApiResponse<OrderApi.OrderResponse> deliverOrder(
            @RequestBody OrderApi.ConfirmOrderRequest request);

    @PostExchange(OrderApi.ORDER_CANCEL)
    ApiResponse<OrderApi.OrderResponse> cancelOrder(
            @RequestBody OrderApi.CancelOrderRequest request);
}
