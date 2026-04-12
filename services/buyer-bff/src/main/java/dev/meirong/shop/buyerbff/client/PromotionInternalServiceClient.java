package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.promotion.PromotionInternalApi;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface PromotionInternalServiceClient {

    @PostExchange(PromotionInternalApi.BUYER_AVAILABLE_COUPONS)
    ApiResponse<PromotionInternalApi.BuyerCouponsResponse> getBuyerAvailableCoupons(
            @RequestBody PromotionInternalApi.BuyerCouponsRequest request);
}
