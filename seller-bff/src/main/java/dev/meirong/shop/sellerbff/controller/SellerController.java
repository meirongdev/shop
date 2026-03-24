package dev.meirong.shop.sellerbff.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.api.MarketplaceApi;
import dev.meirong.shop.contracts.api.OrderApi;
import dev.meirong.shop.contracts.api.ProfileApi;
import dev.meirong.shop.contracts.api.PromotionApi;
import dev.meirong.shop.contracts.api.SearchApi;
import dev.meirong.shop.contracts.api.SellerApi;
import dev.meirong.shop.contracts.api.WalletApi;
import dev.meirong.shop.sellerbff.service.SellerAggregationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SellerApi.BASE_PATH)
public class SellerController {

    private final SellerAggregationService service;

    public SellerController(SellerAggregationService service) {
        this.service = service;
    }

    private String resolveSellerId(String headerSellerId, String bodySellerId) {
        return (headerSellerId == null || headerSellerId.isBlank()) ? bodySellerId : headerSellerId;
    }

    @PostMapping("/dashboard/get")
    public ApiResponse<SellerApi.DashboardResponse> dashboard(@RequestHeader(value = "X-Buyer-Id", required = false) String headerSellerId,
                                                              @Valid @RequestBody SellerApi.SellerContextRequest request) {
        return ApiResponse.success(service.loadDashboard(resolveSellerId(headerSellerId, request.sellerId())));
    }

    @PostMapping("/product/create")
    public ApiResponse<MarketplaceApi.ProductResponse> createProduct(@RequestHeader(value = "X-Buyer-Id", required = false) String headerSellerId,
                                                                     @Valid @RequestBody MarketplaceApi.UpsertProductRequest request) {
        String sellerId = resolveSellerId(headerSellerId, request.sellerId());
        return ApiResponse.success(service.createProduct(new MarketplaceApi.UpsertProductRequest(
                request.productId(), sellerId, request.sku(), request.name(), request.description(),
                request.price(), request.inventory(), request.published())));
    }

    @PostMapping("/product/update")
    public ApiResponse<MarketplaceApi.ProductResponse> updateProduct(@RequestHeader(value = "X-Buyer-Id", required = false) String headerSellerId,
                                                                     @Valid @RequestBody MarketplaceApi.UpsertProductRequest request) {
        String sellerId = resolveSellerId(headerSellerId, request.sellerId());
        return ApiResponse.success(service.updateProduct(new MarketplaceApi.UpsertProductRequest(
                request.productId(), sellerId, request.sku(), request.name(), request.description(),
                request.price(), request.inventory(), request.published())));
    }

    @PostMapping("/promotion/create")
    public ApiResponse<PromotionApi.OfferResponse> createPromotion(@RequestHeader(value = "X-Buyer-Id", required = false) String headerSellerId,
                                                                   @Valid @RequestBody PromotionApi.CreateOfferRequest request) {
        String sellerId = resolveSellerId(headerSellerId, request.sellerId());
        return ApiResponse.success(service.createPromotion(new PromotionApi.CreateOfferRequest(
                sellerId, request.code(), request.title(), request.description(), request.rewardAmount())));
    }

    // ── New e-commerce endpoints ──

    @PostMapping("/order/list")
    public ApiResponse<List<OrderApi.OrderResponse>> listOrders(@RequestHeader(value = "X-Buyer-Id", required = false) String headerSellerId,
                                                                 @Valid @RequestBody SellerApi.SellerContextRequest request) {
        return ApiResponse.success(service.listOrders(resolveSellerId(headerSellerId, request.sellerId())));
    }

    @PostMapping("/order/get")
    public ApiResponse<OrderApi.OrderResponse> getOrder(@Valid @RequestBody OrderApi.GetOrderRequest request) {
        return ApiResponse.success(service.getOrder(request.orderId()));
    }

    @PostMapping("/order/ship")
    public ApiResponse<OrderApi.OrderResponse> shipOrder(@Valid @RequestBody OrderApi.GetOrderRequest request) {
        return ApiResponse.success(service.shipOrder(request.orderId()));
    }

    @PostMapping("/order/deliver")
    public ApiResponse<OrderApi.OrderResponse> deliverOrder(@Valid @RequestBody OrderApi.GetOrderRequest request) {
        return ApiResponse.success(service.deliverOrder(request.orderId()));
    }

    @PostMapping("/wallet/get")
    public ApiResponse<WalletApi.WalletAccountResponse> wallet(@RequestHeader(value = "X-Buyer-Id", required = false) String headerSellerId,
                                                               @Valid @RequestBody SellerApi.SellerContextRequest request) {
        return ApiResponse.success(service.getWallet(resolveSellerId(headerSellerId, request.sellerId())));
    }

    @PostMapping("/profile/get")
    public ApiResponse<ProfileApi.ProfileResponse> profile(@RequestHeader(value = "X-Buyer-Id", required = false) String headerSellerId,
                                                           @Valid @RequestBody SellerApi.SellerContextRequest request) {
        return ApiResponse.success(service.getProfile(resolveSellerId(headerSellerId, request.sellerId())));
    }

    @PostMapping("/profile/update")
    public ApiResponse<ProfileApi.ProfileResponse> updateProfile(@RequestHeader(value = "X-Buyer-Id", required = false) String headerSellerId,
                                                                 @Valid @RequestBody ProfileApi.UpdateProfileRequest request) {
        String sellerId = resolveSellerId(headerSellerId, request.buyerId());
        return ApiResponse.success(service.updateProfile(new ProfileApi.UpdateProfileRequest(
                sellerId,
                request.displayName(),
                request.email(),
                request.tier()
        )));
    }

    @PostMapping("/coupon/create")
    public ApiResponse<PromotionApi.CouponResponse> createCoupon(@RequestHeader(value = "X-Buyer-Id", required = false) String headerSellerId,
                                                                   @Valid @RequestBody PromotionApi.CreateCouponRequest request) {
        String sellerId = resolveSellerId(headerSellerId, request.sellerId());
        return ApiResponse.success(service.createCoupon(new PromotionApi.CreateCouponRequest(
                sellerId, request.code(), request.discountType(), request.discountValue(),
                request.minOrderAmount(), request.maxDiscount(), request.usageLimit(), request.expiresAt())));
    }

    @PostMapping("/coupon/list")
    public ApiResponse<List<PromotionApi.CouponResponse>> listCoupons(@RequestHeader(value = "X-Buyer-Id", required = false) String headerSellerId,
                                                                       @Valid @RequestBody SellerApi.SellerContextRequest request) {
        return ApiResponse.success(service.listCoupons(resolveSellerId(headerSellerId, request.sellerId())));
    }

    @PostMapping("/product/search")
    public ApiResponse<SearchApi.SearchProductsResponse> searchProducts(@Valid @RequestBody MarketplaceApi.SearchProductsRequest request) {
        return ApiResponse.success(service.searchProducts(request));
    }

    // ── Shop Management ──

    @PostMapping("/shop/get")
    public ApiResponse<ProfileApi.SellerStorefrontResponse> getShop(@RequestHeader(value = "X-Buyer-Id", required = false) String headerSellerId,
                                                                     @Valid @RequestBody SellerApi.SellerContextRequest request) {
        return ApiResponse.success(service.getShop(resolveSellerId(headerSellerId, request.sellerId())));
    }

    @PostMapping("/shop/update")
    public ApiResponse<ProfileApi.SellerStorefrontResponse> updateShop(@RequestHeader(value = "X-Buyer-Id", required = false) String headerSellerId,
                                                                        @Valid @RequestBody ProfileApi.UpdateShopRequest request) {
        String sellerId = resolveSellerId(headerSellerId, request.sellerId());
        return ApiResponse.success(service.updateShop(new ProfileApi.UpdateShopRequest(
                sellerId, request.shopName(), request.shopSlug(), request.shopDescription(),
                request.logoUrl(), request.bannerUrl())));
    }
}
