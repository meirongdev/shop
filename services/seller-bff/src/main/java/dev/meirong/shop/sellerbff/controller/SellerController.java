package dev.meirong.shop.sellerbff.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.http.TrustedHeaderNames;
import dev.meirong.shop.contracts.marketplace.MarketplaceApi;
import dev.meirong.shop.contracts.order.OrderApi;
import dev.meirong.shop.contracts.profile.ProfileApi;
import dev.meirong.shop.contracts.promotion.PromotionApi;
import dev.meirong.shop.contracts.search.SearchApi;
import dev.meirong.shop.contracts.seller.SellerApi;
import dev.meirong.shop.contracts.wallet.WalletApi;
import java.util.Arrays;
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

    private static final String SELLER_ROLE = "ROLE_SELLER";

    private final SellerAggregationService service;

    public SellerController(SellerAggregationService service) {
        this.service = service;
    }

    private String resolveSellerId(String headerSellerId, String bodySellerId) {
        return (headerSellerId == null || headerSellerId.isBlank()) ? bodySellerId : headerSellerId;
    }

    private void requireSignedInSeller(String headerRoles, String capability) {
        List<String> roles = headerRoles == null
                ? List.of()
                : Arrays.stream(headerRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .toList();
        if (!roles.contains(SELLER_ROLE)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, capability + " requires a signed-in seller account");
        }
    }

    @PostMapping("/dashboard/get")
    public ApiResponse<SellerApi.DashboardResponse> dashboard(@RequestHeader(value = TrustedHeaderNames.BUYER_ID, required = false) String headerSellerId,
                                                              @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                              @Valid @RequestBody SellerApi.SellerContextRequest request) {
        requireSignedInSeller(headerRoles, "Seller dashboard");
        return ApiResponse.success(service.loadDashboard(resolveSellerId(headerSellerId, request.sellerId())));
    }

    @PostMapping("/product/create")
    public ApiResponse<MarketplaceApi.ProductResponse> createProduct(@RequestHeader(value = TrustedHeaderNames.BUYER_ID, required = false) String headerSellerId,
                                                                     @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                      @Valid @RequestBody MarketplaceApi.UpsertProductRequest request) {
        requireSignedInSeller(headerRoles, "Seller product creation");
        String sellerId = resolveSellerId(headerSellerId, request.sellerId());
        return ApiResponse.success(service.createProduct(new MarketplaceApi.UpsertProductRequest(
                request.productId(), sellerId, request.sku(), request.name(), request.description(),
                request.price(), request.inventory(), request.published())));
    }

    @PostMapping("/product/update")
    public ApiResponse<MarketplaceApi.ProductResponse> updateProduct(@RequestHeader(value = TrustedHeaderNames.BUYER_ID, required = false) String headerSellerId,
                                                                     @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                      @Valid @RequestBody MarketplaceApi.UpsertProductRequest request) {
        requireSignedInSeller(headerRoles, "Seller product updates");
        String sellerId = resolveSellerId(headerSellerId, request.sellerId());
        return ApiResponse.success(service.updateProduct(new MarketplaceApi.UpsertProductRequest(
                request.productId(), sellerId, request.sku(), request.name(), request.description(),
                request.price(), request.inventory(), request.published())));
    }

    @PostMapping("/promotion/create")
    public ApiResponse<PromotionApi.OfferResponse> createPromotion(@RequestHeader(value = TrustedHeaderNames.BUYER_ID, required = false) String headerSellerId,
                                                                   @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                    @Valid @RequestBody PromotionApi.CreateOfferRequest request) {
        requireSignedInSeller(headerRoles, "Seller promotion creation");
        String sellerId = resolveSellerId(headerSellerId, request.sellerId());
        return ApiResponse.success(service.createPromotion(new PromotionApi.CreateOfferRequest(
                sellerId, request.code(), request.title(), request.description(), request.rewardAmount())));
    }

    // ── New e-commerce endpoints ──

    @PostMapping("/order/list")
    public ApiResponse<List<OrderApi.OrderResponse>> listOrders(@RequestHeader(value = TrustedHeaderNames.BUYER_ID, required = false) String headerSellerId,
                                                                 @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                  @Valid @RequestBody SellerApi.SellerContextRequest request) {
        requireSignedInSeller(headerRoles, "Seller order history");
        return ApiResponse.success(service.listOrders(resolveSellerId(headerSellerId, request.sellerId())));
    }

    @PostMapping("/order/get")
    public ApiResponse<OrderApi.OrderResponse> getOrder(@RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                        @Valid @RequestBody OrderApi.GetOrderRequest request) {
        requireSignedInSeller(headerRoles, "Seller order details");
        return ApiResponse.success(service.getOrder(request.orderId()));
    }

    @PostMapping("/order/ship")
    public ApiResponse<OrderApi.OrderResponse> shipOrder(@RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                         @Valid @RequestBody OrderApi.GetOrderRequest request) {
        requireSignedInSeller(headerRoles, "Seller order shipment");
        return ApiResponse.success(service.shipOrder(request.orderId()));
    }

    @PostMapping("/order/deliver")
    public ApiResponse<OrderApi.OrderResponse> deliverOrder(@RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                            @Valid @RequestBody OrderApi.GetOrderRequest request) {
        requireSignedInSeller(headerRoles, "Seller order delivery");
        return ApiResponse.success(service.deliverOrder(request.orderId()));
    }

    @PostMapping("/order/cancel")
    public ApiResponse<OrderApi.OrderResponse> cancelOrder(@RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                           @Valid @RequestBody OrderApi.CancelOrderRequest request) {
        requireSignedInSeller(headerRoles, "Seller order cancellation");
        return ApiResponse.success(service.cancelOrder(request.orderId(), request.reason()));
    }

    @PostMapping("/wallet/get")
    public ApiResponse<WalletApi.WalletAccountResponse> wallet(@RequestHeader(value = TrustedHeaderNames.BUYER_ID, required = false) String headerSellerId,
                                                               @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                @Valid @RequestBody SellerApi.SellerContextRequest request) {
        requireSignedInSeller(headerRoles, "Seller wallet");
        return ApiResponse.success(service.getWallet(resolveSellerId(headerSellerId, request.sellerId())));
    }

    @PostMapping("/wallet/withdraw")
    public ApiResponse<WalletApi.TransactionResponse> withdraw(@RequestHeader(value = TrustedHeaderNames.BUYER_ID, required = false) String headerSellerId,
                                                                @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                 @Valid @RequestBody WalletApi.WithdrawRequest request) {
        requireSignedInSeller(headerRoles, "Seller wallet withdrawals");
        String sellerId = resolveSellerId(headerSellerId, request.buyerId());
        return ApiResponse.success(service.withdrawWallet(new WalletApi.WithdrawRequest(sellerId, request.amount(), request.currency())));
    }

    @PostMapping("/profile/get")
    public ApiResponse<ProfileApi.ProfileResponse> profile(@RequestHeader(value = TrustedHeaderNames.BUYER_ID, required = false) String headerSellerId,
                                                           @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                            @Valid @RequestBody SellerApi.SellerContextRequest request) {
        requireSignedInSeller(headerRoles, "Seller profile");
        return ApiResponse.success(service.getProfile(resolveSellerId(headerSellerId, request.sellerId())));
    }

    @PostMapping("/profile/update")
    public ApiResponse<ProfileApi.ProfileResponse> updateProfile(@RequestHeader(value = TrustedHeaderNames.BUYER_ID, required = false) String headerSellerId,
                                                                 @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                  @Valid @RequestBody ProfileApi.UpdateProfileRequest request) {
        requireSignedInSeller(headerRoles, "Seller profile updates");
        String sellerId = resolveSellerId(headerSellerId, request.buyerId());
        return ApiResponse.success(service.updateProfile(new ProfileApi.UpdateProfileRequest(
                sellerId,
                request.displayName(),
                request.email(),
                request.tier()
        )));
    }

    @PostMapping("/coupon/create")
    public ApiResponse<PromotionApi.CouponResponse> createCoupon(@RequestHeader(value = TrustedHeaderNames.BUYER_ID, required = false) String headerSellerId,
                                                                 @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                    @Valid @RequestBody PromotionApi.CreateCouponRequest request) {
        requireSignedInSeller(headerRoles, "Seller coupon creation");
        String sellerId = resolveSellerId(headerSellerId, request.sellerId());
        return ApiResponse.success(service.createCoupon(new PromotionApi.CreateCouponRequest(
                sellerId, request.code(), request.discountType(), request.discountValue(),
                request.minOrderAmount(), request.maxDiscount(), request.usageLimit(), request.expiresAt())));
    }

    @PostMapping("/coupon/list")
    public ApiResponse<List<PromotionApi.CouponResponse>> listCoupons(@RequestHeader(value = TrustedHeaderNames.BUYER_ID, required = false) String headerSellerId,
                                                                      @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                        @Valid @RequestBody SellerApi.SellerContextRequest request) {
        requireSignedInSeller(headerRoles, "Seller coupons");
        return ApiResponse.success(service.listCoupons(resolveSellerId(headerSellerId, request.sellerId())));
    }

    @PostMapping("/product/search")
    public ApiResponse<SearchApi.SearchProductsResponse> searchProducts(@RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                       @Valid @RequestBody MarketplaceApi.SearchProductsRequest request) {
        requireSignedInSeller(headerRoles, "Seller product search");
        return ApiResponse.success(service.searchProducts(request));
    }

    // ── Shop Management ──

    @PostMapping("/shop/get")
    public ApiResponse<ProfileApi.SellerStorefrontResponse> getShop(@RequestHeader(value = TrustedHeaderNames.BUYER_ID, required = false) String headerSellerId,
                                                                    @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                      @Valid @RequestBody SellerApi.SellerContextRequest request) {
        requireSignedInSeller(headerRoles, "Seller shop");
        return ApiResponse.success(service.getShop(resolveSellerId(headerSellerId, request.sellerId())));
    }

    @PostMapping("/shop/update")
    public ApiResponse<ProfileApi.SellerStorefrontResponse> updateShop(@RequestHeader(value = TrustedHeaderNames.BUYER_ID, required = false) String headerSellerId,
                                                                       @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                         @Valid @RequestBody ProfileApi.UpdateShopRequest request) {
        requireSignedInSeller(headerRoles, "Seller shop updates");
        String sellerId = resolveSellerId(headerSellerId, request.sellerId());
        return ApiResponse.success(service.updateShop(new ProfileApi.UpdateShopRequest(
                sellerId, request.shopName(), request.shopSlug(), request.shopDescription(),
                request.logoUrl(), request.bannerUrl())));
    }
}
