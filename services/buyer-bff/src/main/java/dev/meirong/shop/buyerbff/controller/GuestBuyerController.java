package dev.meirong.shop.buyerbff.controller;

import dev.meirong.shop.buyerbff.service.BuyerAggregationService;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.buyer.BuyerApi;
import dev.meirong.shop.contracts.order.OrderApi;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(BuyerApi.BASE_PATH + "/guest")
public class GuestBuyerController {

    private final BuyerAggregationService service;

    public GuestBuyerController(BuyerAggregationService service) {
        this.service = service;
    }

    @PostMapping("/checkout")
    public ApiResponse<OrderApi.OrderResponse> guestCheckout(@Valid @RequestBody OrderApi.GuestCheckoutRequest request) {
        return ApiResponse.success(service.guestCheckout(request));
    }

    @GetMapping("/order/track")
    public ApiResponse<OrderApi.OrderResponse> trackOrder(@RequestParam String token) {
        return ApiResponse.success(service.trackOrder(token));
    }
}
