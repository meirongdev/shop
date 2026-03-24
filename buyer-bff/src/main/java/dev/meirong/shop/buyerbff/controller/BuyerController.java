package dev.meirong.shop.buyerbff.controller;

import dev.meirong.shop.buyerbff.service.BuyerAggregationService;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.http.TrustedHeaderNames;
import dev.meirong.shop.contracts.api.BuyerApi;
import dev.meirong.shop.contracts.api.LoyaltyApi;
import dev.meirong.shop.contracts.api.MarketplaceApi;
import dev.meirong.shop.contracts.api.OrderApi;
import dev.meirong.shop.contracts.api.ProfileApi;
import dev.meirong.shop.contracts.api.PromotionApi;
import dev.meirong.shop.contracts.api.SearchApi;
import dev.meirong.shop.contracts.api.WalletApi;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(BuyerApi.BASE_PATH)
public class BuyerController {

    private static final String BUYER_ROLE = "ROLE_BUYER";
    private static final String GUEST_BUYER_ROLE = "ROLE_BUYER_GUEST";

    private final BuyerAggregationService service;
    private final MeterRegistry meterRegistry;

    public BuyerController(BuyerAggregationService service, MeterRegistry meterRegistry) {
        this.service = service;
        this.meterRegistry = meterRegistry;
    }

    private String resolvePlayerId(String headerPlayerId, String bodyPlayerId) {
        return (headerPlayerId == null || headerPlayerId.isBlank()) ? bodyPlayerId : headerPlayerId;
    }

    private String requireAuthenticatedPlayerId(String headerPlayerId, String capability) {
        if (headerPlayerId == null || headerPlayerId.isBlank()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, capability + " requires authenticated buyer context");
        }
        return headerPlayerId;
    }

    private void requireSignedInBuyer(String headerRoles, String capability) {
        List<String> roles = headerRoles == null
                ? List.of()
                : Arrays.stream(headerRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .toList();
        if (roles.contains(GUEST_BUYER_ROLE) || !roles.contains(BUYER_ROLE)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, capability + " requires a signed-in buyer account");
        }
    }

    @PostMapping("/dashboard/get")
    public ApiResponse<BuyerApi.DashboardResponse> dashboard(@RequestHeader(value = "X-Player-Id", required = false) String headerPlayerId,
                                                             @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                             @Valid @RequestBody BuyerApi.BuyerContextRequest request) {
        requireSignedInBuyer(headerRoles, "Buyer dashboard");
        return ApiResponse.success(service.loadDashboard(resolvePlayerId(headerPlayerId, request.playerId())));
    }

    @PostMapping("/profile/get")
    public ApiResponse<ProfileApi.ProfileResponse> profile(@RequestHeader(value = "X-Player-Id", required = false) String headerPlayerId,
                                                           @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                           @Valid @RequestBody BuyerApi.BuyerContextRequest request) {
        requireSignedInBuyer(headerRoles, "Buyer profile");
        return ApiResponse.success(service.getProfile(resolvePlayerId(headerPlayerId, request.playerId())));
    }

    @PostMapping("/profile/update")
    public ApiResponse<ProfileApi.ProfileResponse> updateProfile(@RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                 @Valid @RequestBody ProfileApi.UpdateProfileRequest request) {
        requireSignedInBuyer(headerRoles, "Buyer profile updates");
        return ApiResponse.success(service.updateProfile(request));
    }

    @PostMapping("/wallet/get")
    public ApiResponse<WalletApi.WalletAccountResponse> wallet(@RequestHeader(value = "X-Player-Id", required = false) String headerPlayerId,
                                                               @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                               @Valid @RequestBody BuyerApi.BuyerContextRequest request) {
        requireSignedInBuyer(headerRoles, "Buyer wallet");
        return ApiResponse.success(service.getWallet(resolvePlayerId(headerPlayerId, request.playerId())));
    }

    @PostMapping("/wallet/deposit")
    public ApiResponse<WalletApi.TransactionResponse> deposit(@RequestHeader(value = "X-Player-Id", required = false) String headerPlayerId,
                                                              @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                              @Valid @RequestBody WalletApi.DepositRequest request) {
        requireSignedInBuyer(headerRoles, "Buyer wallet deposits");
        String playerId = resolvePlayerId(headerPlayerId, request.playerId());
        return ApiResponse.success(service.deposit(new WalletApi.DepositRequest(playerId, request.amount(), request.currency())));
    }

    @PostMapping("/wallet/withdraw")
    public ApiResponse<WalletApi.TransactionResponse> withdraw(@RequestHeader(value = "X-Player-Id", required = false) String headerPlayerId,
                                                               @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                               @Valid @RequestBody WalletApi.WithdrawRequest request) {
        requireSignedInBuyer(headerRoles, "Buyer wallet withdrawals");
        String playerId = resolvePlayerId(headerPlayerId, request.playerId());
        return ApiResponse.success(service.withdraw(new WalletApi.WithdrawRequest(playerId, request.amount(), request.currency())));
    }

    @PostMapping("/promotion/list")
    public ApiResponse<List<PromotionApi.OfferResponse>> promotions() {
        return ApiResponse.success(service.listPromotions());
    }

    @PostMapping("/coupon/list")
    public ApiResponse<List<PromotionApi.CouponResponse>> coupons() {
        return ApiResponse.success(service.listCoupons());
    }

    @PostMapping("/marketplace/list")
    public ApiResponse<List<MarketplaceApi.ProductResponse>> marketplace() {
        return ApiResponse.success(service.listMarketplace());
    }

    // ── New e-commerce endpoints ──

    @PostMapping("/product/get")
    public ApiResponse<MarketplaceApi.ProductResponse> getProduct(@Valid @RequestBody MarketplaceApi.GetProductRequest request) {
        return ApiResponse.success(service.getProduct(request.productId()));
    }

    @PostMapping("/product/search")
    public ApiResponse<SearchApi.SearchProductsResponse> searchProducts(@RequestBody MarketplaceApi.SearchProductsRequest request) {
        return ApiResponse.success(service.searchProducts(request));
    }

    @PostMapping("/category/list")
    public ApiResponse<List<MarketplaceApi.CategoryResponse>> listCategories() {
        return ApiResponse.success(service.listCategories());
    }

    // ── Seller Storefront ──

    @PostMapping("/shop/get")
    public ApiResponse<ProfileApi.SellerStorefrontResponse> getSellerShop(@Valid @RequestBody ProfileApi.GetProfileRequest request) {
        return ApiResponse.success(service.getSellerStorefront(request.playerId()));
    }

    @PostMapping("/cart/list")
    public ApiResponse<OrderApi.CartView> listCart(@RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
                                                   @Valid @RequestBody BuyerApi.BuyerContextRequest request) {
        return ApiResponse.success(service.listCart(resolvePlayerId(headerPlayerId, request.playerId())));
    }

    @PostMapping("/cart/add")
    public ApiResponse<OrderApi.CartItemResponse> addToCart(
            @RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
            @Valid @RequestBody OrderApi.AddToCartRequest request) {
        String buyerId = resolvePlayerId(headerPlayerId, request.buyerId());
        return ApiResponse.success(service.addToCart(new OrderApi.AddToCartRequest(
                buyerId,
                request.productId(),
                request.productName(),
                request.productPrice(),
                request.sellerId(),
                request.quantity())));
    }

    @PostMapping("/cart/update")
    public ApiResponse<OrderApi.CartItemResponse> updateCart(
            @RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
            @Valid @RequestBody OrderApi.UpdateCartRequest request) {
        String buyerId = resolvePlayerId(headerPlayerId, request.buyerId());
        return ApiResponse.success(service.updateCart(new OrderApi.UpdateCartRequest(
                buyerId,
                request.productId(),
                request.quantity())));
    }

    @PostMapping("/cart/remove")
    public ApiResponse<Void> removeFromCart(
            @RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
            @Valid @RequestBody OrderApi.RemoveFromCartRequest request) {
        String buyerId = resolvePlayerId(headerPlayerId, request.buyerId());
        service.removeFromCart(new OrderApi.RemoveFromCartRequest(buyerId, request.productId()));
        return ApiResponse.success(null);
    }

    @PostMapping("/cart/merge")
    public ApiResponse<OrderApi.CartView> mergeCart(
            @RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
            @Valid @RequestBody BuyerApi.MergeGuestCartRequest request) {
        requireSignedInBuyer(headerRoles, "Buyer cart merge");
        return ApiResponse.success(service.mergeGuestCart(
                requireAuthenticatedPlayerId(headerPlayerId, "Buyer cart merge"),
                request.guestPlayerId()));
    }

    @PostMapping("/checkout/create")
    public ApiResponse<BuyerApi.CheckoutResponse> checkout(
                                                            @RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
                                                            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                            @Valid @RequestBody BuyerApi.CheckoutRequest request) {
        requireSignedInBuyer(headerRoles, "Buyer checkout");
        String provider = normalizeProvider(request.paymentMethod());
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "success";
        try {
            return ApiResponse.success(service.checkout(new BuyerApi.CheckoutRequest(
                    resolvePlayerId(headerPlayerId, request.playerId()),
                    request.couponCode(),
                    request.paymentMethod(),
                    request.pointsToUse())));
        } catch (RuntimeException exception) {
            result = "failure";
            throw exception;
        } finally {
            sample.stop(Timer.builder("shop_order_checkout_duration_seconds")
                    .tag("service", "buyer-bff")
                    .tag("operation", "checkout")
                    .tag("provider", provider)
                    .tag("result", result)
                    .register(meterRegistry));
        }
    }

    @GetMapping("/payment/methods")
    public ApiResponse<List<WalletApi.PaymentMethodInfo>> listPaymentMethods() {
        return ApiResponse.success(service.listPaymentMethods());
    }

    @PostMapping("/payment/intent")
    public ApiResponse<WalletApi.PaymentIntentResponse> createPaymentIntent(
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
            @Valid @RequestBody WalletApi.CreatePaymentIntentRequest request) {
        requireSignedInBuyer(headerRoles, "Create payment intent");
        return ApiResponse.success(service.createPaymentIntent(request));
    }

    @PostMapping("/order/list")
    public ApiResponse<List<OrderApi.OrderResponse>> listOrders(@RequestHeader(value = "X-Player-Id", required = false) String headerPlayerId,
                                                                @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                                @Valid @RequestBody BuyerApi.BuyerContextRequest request) {
        requireSignedInBuyer(headerRoles, "Buyer order history");
        return ApiResponse.success(service.listOrders(resolvePlayerId(headerPlayerId, request.playerId()), "buyer"));
    }

    @PostMapping("/order/get")
    public ApiResponse<OrderApi.OrderResponse> getOrder(@RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                        @Valid @RequestBody OrderApi.GetOrderRequest request) {
        requireSignedInBuyer(headerRoles, "Buyer order details");
        return ApiResponse.success(service.getOrder(request.orderId()));
    }

    @PostMapping("/order/cancel")
    public ApiResponse<OrderApi.OrderResponse> cancelOrder(@RequestHeader(value = "X-Player-Id", required = false) String headerPlayerId,
                                                           @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
                                                           @Valid @RequestBody OrderApi.CancelOrderRequest request) {
        requireSignedInBuyer(headerRoles, "Buyer order cancellation");
        return ApiResponse.success(service.cancelOrder(request.orderId(), headerPlayerId));
    }

    // ── Loyalty ──

    @GetMapping("/loyalty/account")
    public ApiResponse<LoyaltyApi.AccountResponse> loyaltyAccount(
            @RequestHeader(value = "X-Player-Id", required = false) String headerPlayerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles) {
        requireSignedInBuyer(headerRoles, "Loyalty account");
        return ApiResponse.success(service.getLoyaltyAccount(headerPlayerId));
    }

    private String normalizeProvider(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "wallet";
        }
        return paymentMethod.toLowerCase(Locale.ROOT);
    }

    @GetMapping("/loyalty/hub")
    public ApiResponse<BuyerApi.LoyaltyHubResponse> loyaltyHub(
            @RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles) {
        requireSignedInBuyer(headerRoles, "Loyalty hub");
        return ApiResponse.success(service.loadLoyaltyHub(requireAuthenticatedPlayerId(headerPlayerId, "Loyalty hub")));
    }

    @PostMapping("/loyalty/checkin")
    public ApiResponse<LoyaltyApi.CheckinResponse> loyaltyCheckin(
            @RequestHeader(value = "X-Player-Id", required = false) String headerPlayerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles) {
        requireSignedInBuyer(headerRoles, "Loyalty check-in");
        return ApiResponse.success(service.loyaltyCheckin(headerPlayerId));
    }

    @GetMapping("/loyalty/checkin/calendar")
    public ApiResponse<List<LoyaltyApi.CheckinResponse>> loyaltyCalendar(
            @RequestHeader(value = "X-Player-Id", required = false) String headerPlayerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
            @RequestParam int year, @RequestParam int month) {
        requireSignedInBuyer(headerRoles, "Loyalty calendar");
        return ApiResponse.success(service.loyaltyCheckinCalendar(headerPlayerId, year, month));
    }

    @GetMapping("/loyalty/transactions")
    public ApiResponse<List<LoyaltyApi.TransactionResponse>> loyaltyTransactions(
            @RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireSignedInBuyer(headerRoles, "Loyalty transactions");
        return ApiResponse.success(service.getLoyaltyTransactions(
                requireAuthenticatedPlayerId(headerPlayerId, "Loyalty transactions"), page, size));
    }

    @GetMapping("/loyalty/rewards")
    public ApiResponse<List<LoyaltyApi.RewardItemResponse>> loyaltyRewards(
            @RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles) {
        requireSignedInBuyer(headerRoles, "Loyalty rewards");
        requireAuthenticatedPlayerId(headerPlayerId, "Loyalty rewards");
        return ApiResponse.success(service.listLoyaltyRewards());
    }

    @PostMapping("/loyalty/rewards/redeem")
    public ApiResponse<LoyaltyApi.RedemptionResponse> redeemLoyaltyReward(
            @RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
            @Valid @RequestBody LoyaltyApi.RedeemRequest request) {
        requireSignedInBuyer(headerRoles, "Loyalty redemption");
        return ApiResponse.success(service.redeemLoyaltyReward(
                requireAuthenticatedPlayerId(headerPlayerId, "Loyalty redemption"), request));
    }

    @GetMapping("/loyalty/redemptions")
    public ApiResponse<List<LoyaltyApi.RedemptionResponse>> loyaltyRedemptions(
            @RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireSignedInBuyer(headerRoles, "Loyalty redemptions");
        return ApiResponse.success(service.getLoyaltyRedemptions(
                requireAuthenticatedPlayerId(headerPlayerId, "Loyalty redemptions"), page, size));
    }

    @GetMapping("/loyalty/onboarding/tasks")
    public ApiResponse<List<LoyaltyApi.OnboardingTaskResponse>> loyaltyOnboardingTasks(
            @RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles) {
        requireSignedInBuyer(headerRoles, "Loyalty onboarding tasks");
        return ApiResponse.success(service.getLoyaltyOnboardingTasks(
                requireAuthenticatedPlayerId(headerPlayerId, "Loyalty onboarding tasks")));
    }

    @GetMapping("/welcome/summary")
    public ApiResponse<BuyerApi.WelcomeSummaryResponse> welcomeSummary(
            @RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles) {
        requireSignedInBuyer(headerRoles, "Welcome summary");
        return ApiResponse.success(service.loadWelcomeSummary(
                requireAuthenticatedPlayerId(headerPlayerId, "Welcome summary")));
    }

    @GetMapping("/invite/stats")
    public ApiResponse<BuyerApi.InviteStatsResponse> inviteStats(
            @RequestHeader(value = TrustedHeaderNames.PLAYER_ID, required = false) String headerPlayerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles) {
        requireSignedInBuyer(headerRoles, "Invite stats");
        return ApiResponse.success(service.getInviteStats(
                requireAuthenticatedPlayerId(headerPlayerId, "Invite stats")));
    }
}
