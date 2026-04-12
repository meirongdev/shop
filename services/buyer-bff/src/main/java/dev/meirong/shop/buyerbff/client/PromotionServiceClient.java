package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.promotion.PromotionApi;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface PromotionServiceClient {

    @PostExchange(PromotionApi.LIST)
    ApiResponse<PromotionApi.OffersView> listOffers(
            @RequestBody PromotionApi.ListOffersRequest request);

    @PostExchange(PromotionApi.COUPON_LIST)
    ApiResponse<List<PromotionApi.CouponResponse>> listCoupons(
            @RequestBody PromotionApi.ListCouponsRequest request);

    @PostExchange(PromotionApi.COUPON_VALIDATE)
    ApiResponse<PromotionApi.CouponValidationResponse> validateCoupon(
            @RequestBody PromotionApi.ValidateCouponRequest request);

    @PostExchange(PromotionApi.COUPON_APPLY)
    ApiResponse<Void> applyCoupon(
            @RequestBody PromotionApi.ApplyCouponRequest request);
}
