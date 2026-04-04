package dev.meirong.shop.promotion.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.api.PromotionInternalApi;
import dev.meirong.shop.promotion.service.CouponTemplateService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping(PromotionInternalApi.BASE_PATH)
public class PromotionInternalController {

    private final CouponTemplateService couponTemplateService;

    public PromotionInternalController(CouponTemplateService couponTemplateService) {
        this.couponTemplateService = couponTemplateService;
    }

    @PostMapping("/coupon/buyer")
    public ApiResponse<PromotionInternalApi.BuyerCouponsResponse> buyerCoupons(
            @Valid @RequestBody PromotionInternalApi.BuyerCouponsRequest request) {
        return ApiResponse.success(couponTemplateService.listBuyerCouponSummaries(request.buyerId()));
    }
}
