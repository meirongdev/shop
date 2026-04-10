package dev.meirong.shop.promotion.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.promotion.PromotionApi;
import dev.meirong.shop.promotion.engine.PromotionContext;
import dev.meirong.shop.promotion.engine.PromotionEngine;
import dev.meirong.shop.promotion.service.CouponApplicationService;
import dev.meirong.shop.promotion.service.PromotionApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(PromotionApi.BASE_PATH)
public class PromotionController {

    private final PromotionApplicationService service;
    private final CouponApplicationService couponService;
    private final PromotionEngine promotionEngine;

    public PromotionController(PromotionApplicationService service,
                               CouponApplicationService couponService,
                               PromotionEngine promotionEngine) {
        this.service = service;
        this.couponService = couponService;
        this.promotionEngine = promotionEngine;
    }

    @PostMapping("/offer/list")
    public ApiResponse<PromotionApi.OffersView> listOffers(@RequestBody PromotionApi.ListOffersRequest request) {
        return ApiResponse.success(service.listOffers(request));
    }

    @PostMapping("/offer/create")
    public ApiResponse<PromotionApi.OfferResponse> createOffer(@Valid @RequestBody PromotionApi.CreateOfferRequest request) {
        return ApiResponse.success(service.createOffer(request));
    }

    @PostMapping("/coupon/create")
    public ApiResponse<PromotionApi.CouponResponse> createCoupon(@Valid @RequestBody PromotionApi.CreateCouponRequest request) {
        return ApiResponse.success(couponService.createCoupon(request));
    }

    @PostMapping("/coupon/list")
    public ApiResponse<List<PromotionApi.CouponResponse>> listCoupons(@RequestBody PromotionApi.ListCouponsRequest request) {
        return ApiResponse.success(couponService.listCoupons(request.sellerId()));
    }

    @PostMapping("/coupon/validate")
    public ApiResponse<PromotionApi.CouponValidationResponse> validateCoupon(@Valid @RequestBody PromotionApi.ValidateCouponRequest request) {
        return ApiResponse.success(couponService.validateCoupon(request));
    }

    @PostMapping("/coupon/apply")
    public ApiResponse<Void> applyCoupon(@Valid @RequestBody PromotionApi.ApplyCouponRequest request) {
        couponService.applyCoupon(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/calculate")
    public ApiResponse<PromotionApi.CalculateResponse> calculate(
            @Valid @RequestBody PromotionApi.CalculateRequest request) {
        List<PromotionContext.CartItem> items = request.items() != null
                ? request.items().stream()
                    .map(i -> new PromotionContext.CartItem(i.productId(), i.categoryId(), i.price(), i.quantity()))
                    .toList()
                : List.of();

        PromotionContext context = new PromotionContext(
                request.buyerId(), request.orderAmount(), items,
                request.userTier(), request.isNewUser());

        PromotionEngine.CalculationResult result = promotionEngine.calculate(context);

        List<PromotionApi.DiscountLineResponse> lines = result.appliedDiscounts().stream()
                .map(d -> new PromotionApi.DiscountLineResponse(d.offerId(), d.offerCode(), d.offerTitle(), d.discount()))
                .toList();

        return ApiResponse.success(new PromotionApi.CalculateResponse(
                result.originalAmount(), result.totalDiscount(), result.finalAmount(), lines));
    }
}
