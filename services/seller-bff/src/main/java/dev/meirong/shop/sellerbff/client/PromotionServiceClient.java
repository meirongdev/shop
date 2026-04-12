package dev.meirong.shop.sellerbff.client;

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

    @PostExchange(PromotionApi.CREATE)
    ApiResponse<PromotionApi.OfferResponse> createOffer(
            @RequestBody PromotionApi.CreateOfferRequest request);

    @PostExchange(PromotionApi.COUPON_CREATE)
    ApiResponse<PromotionApi.CouponResponse> createCoupon(
            @RequestBody PromotionApi.CreateCouponRequest request);

    @PostExchange(PromotionApi.COUPON_LIST)
    ApiResponse<List<PromotionApi.CouponResponse>> listCoupons(
            @RequestBody PromotionApi.ListCouponsRequest request);
}
