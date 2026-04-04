package dev.meirong.shop.order.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.api.OrderApi;
import dev.meirong.shop.order.service.OrderApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalOrderController {

    private final OrderApplicationService orderService;

    public InternalOrderController(OrderApplicationService orderService) {
        this.orderService = orderService;
    }

    @PostMapping(OrderApi.INTERNAL_PAYMENT_CONFIRM)
    public ApiResponse<OrderApi.OrderResponse> confirmPayment(
            @Valid @RequestBody OrderApi.PaymentConfirmRequest request) {
        return ApiResponse.success(orderService.confirmPayment(request));
    }
}
