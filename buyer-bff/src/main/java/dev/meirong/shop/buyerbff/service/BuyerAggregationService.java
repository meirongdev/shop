package dev.meirong.shop.buyerbff.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.buyerbff.client.SearchServiceClient;
import dev.meirong.shop.buyerbff.config.BuyerClientProperties;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.http.TrustedHeaderNames;
import dev.meirong.shop.common.resilience.ResilienceHelper;
import dev.meirong.shop.contracts.api.BuyerApi;
import dev.meirong.shop.contracts.api.LoyaltyApi;
import dev.meirong.shop.contracts.api.MarketplaceApi;
import dev.meirong.shop.contracts.api.OrderApi;
import dev.meirong.shop.contracts.api.ProfileApi;
import dev.meirong.shop.contracts.api.ProfileInternalApi;
import dev.meirong.shop.contracts.api.PromotionApi;
import dev.meirong.shop.contracts.api.PromotionInternalApi;
import dev.meirong.shop.contracts.api.SearchApi;
import dev.meirong.shop.contracts.api.WalletApi;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class BuyerAggregationService {

    private static final Logger log = LoggerFactory.getLogger(BuyerAggregationService.class);

    private final RestClient restClient;
    private final SearchServiceClient searchServiceClient;
    private final BuyerClientProperties properties;
    private final ResilienceHelper resilienceHelper;
    private final GuestCartStore guestCartStore;
    private final ObjectMapper objectMapper;

    public BuyerAggregationService(RestClient.Builder builder,
                                    SearchServiceClient searchServiceClient,
                                    BuyerClientProperties properties,
                                    ResilienceHelper resilienceHelper,
                                    GuestCartStore guestCartStore,
                                    ObjectMapper objectMapper) {
        this.restClient = builder.build();
        this.searchServiceClient = searchServiceClient;
        this.properties = properties;
        this.resilienceHelper = resilienceHelper;
        this.guestCartStore = guestCartStore;
        this.objectMapper = objectMapper;
    }

    public BuyerApi.DashboardResponse loadDashboard(String buyerId) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var profileFuture = executor.submit(() -> getProfile(buyerId));
            var walletFuture = executor.submit(() -> getWallet(buyerId));
            var promotionsFuture = executor.submit(this::listPromotions);
            var marketplaceFuture = executor.submit(this::listMarketplace);
            var loyaltyFuture = executor.submit(() -> getLoyaltyAccount(buyerId));
            return new BuyerApi.DashboardResponse(
                    profileFuture.get(),
                    walletFuture.get(),
                    promotionsFuture.get(),
                    marketplaceFuture.get(),
                    loyaltyFuture.get()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Buyer dashboard interrupted", exception);
        } catch (ExecutionException exception) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Buyer dashboard aggregation failed", exception);
        }
    }

    // ── Profile ──

    public ProfileApi.ProfileResponse getProfile(String buyerId) {
        return call("profileService", true,
                () -> post(properties.profileServiceUrl() + ProfileApi.GET,
                        new ProfileApi.GetProfileRequest(buyerId),
                        new ParameterizedTypeReference<ApiResponse<ProfileApi.ProfileResponse>>() {}),
                "Profile service is temporarily unavailable");
    }

    public ProfileApi.ProfileResponse updateProfile(ProfileApi.UpdateProfileRequest request) {
        return call("profileService", false,
                () -> post(properties.profileServiceUrl() + ProfileApi.UPDATE, request,
                        new ParameterizedTypeReference<ApiResponse<ProfileApi.ProfileResponse>>() {}),
                "Profile service is temporarily unavailable");
    }

    // ── Wallet ──

    public WalletApi.WalletAccountResponse getWallet(String buyerId) {
        return call("walletService", false,
                () -> post(properties.walletServiceUrl() + WalletApi.GET,
                        new WalletApi.GetWalletRequest(buyerId),
                        new ParameterizedTypeReference<ApiResponse<WalletApi.WalletAccountResponse>>() {}),
                "Wallet service is temporarily unavailable");
    }

    public WalletApi.TransactionResponse deposit(WalletApi.DepositRequest request) {
        return call("walletService", false,
                () -> post(properties.walletServiceUrl() + WalletApi.DEPOSIT, request,
                        new ParameterizedTypeReference<ApiResponse<WalletApi.TransactionResponse>>() {}),
                "Wallet service is temporarily unavailable");
    }

    public WalletApi.TransactionResponse withdraw(WalletApi.WithdrawRequest request) {
        return call("walletService", false,
                () -> post(properties.walletServiceUrl() + WalletApi.WITHDRAW, request,
                        new ParameterizedTypeReference<ApiResponse<WalletApi.TransactionResponse>>() {}),
                "Wallet service is temporarily unavailable");
    }

    public List<WalletApi.PaymentMethodInfo> listPaymentMethods() {
        return call("walletService", false,
                () -> post(properties.walletServiceUrl() + WalletApi.PAYMENT_METHODS, Map.of(),
                        new ParameterizedTypeReference<ApiResponse<List<WalletApi.PaymentMethodInfo>>>() {}),
                "Wallet service is temporarily unavailable");
    }

    public WalletApi.PaymentIntentResponse createPaymentIntent(WalletApi.CreatePaymentIntentRequest request) {
        return call("walletService", false,
                () -> post(properties.walletServiceUrl() + WalletApi.PAYMENT_INTENT, request,
                        new ParameterizedTypeReference<ApiResponse<WalletApi.PaymentIntentResponse>>() {}),
                "Wallet service is temporarily unavailable");
    }

    // ── Loyalty ──

    public LoyaltyApi.AccountResponse getLoyaltyAccount(String buyerId) {
        return call("loyaltyService", false,
                () -> get(properties.loyaltyServiceUrl() + LoyaltyApi.INTERNAL_BALANCE + "/" + buyerId,
                        new ParameterizedTypeReference<ApiResponse<LoyaltyApi.AccountResponse>>() {}),
                "Loyalty service is temporarily unavailable");
    }

    public LoyaltyApi.CheckinResponse loyaltyCheckin(String buyerId) {
        return call("loyaltyService", false,
                () -> postWithHeader(properties.loyaltyServiceUrl() + LoyaltyApi.CHECKIN, Map.of(), buyerId,
                        new ParameterizedTypeReference<ApiResponse<LoyaltyApi.CheckinResponse>>() {}),
                "Loyalty service is temporarily unavailable");
    }

    public BuyerApi.LoyaltyHubResponse loadLoyaltyHub(String buyerId) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var accountFuture = executor.submit(() -> getLoyaltyAccount(buyerId));
            var tasksFuture = executor.submit(() -> getLoyaltyOnboardingTasks(buyerId));
            var rewardsFuture = executor.submit(this::listLoyaltyRewards);
            var transactionsFuture = executor.submit(() -> getLoyaltyTransactions(buyerId, 0, 10));
            var redemptionsFuture = executor.submit(() -> getLoyaltyRedemptions(buyerId, 0, 10));
            return new BuyerApi.LoyaltyHubResponse(
                    accountFuture.get(),
                    tasksFuture.get(),
                    rewardsFuture.get(),
                    transactionsFuture.get(),
                    redemptionsFuture.get());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Loyalty hub interrupted", exception);
        } catch (ExecutionException exception) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Loyalty hub aggregation failed", exception);
        }
    }

    public List<LoyaltyApi.CheckinResponse> loyaltyCheckinCalendar(String buyerId, int year, int month) {
        String url = properties.loyaltyServiceUrl() + LoyaltyApi.CHECKIN_CALENDAR
                + "?year=" + year + "&month=" + month;
        return call("loyaltyService", false,
                () -> getWithHeader(url, buyerId,
                        new ParameterizedTypeReference<ApiResponse<List<LoyaltyApi.CheckinResponse>>>() {}),
                "Loyalty service is temporarily unavailable");
    }

    public List<LoyaltyApi.TransactionResponse> getLoyaltyTransactions(String buyerId, int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.loyaltyServiceUrl() + LoyaltyApi.TRANSACTIONS)
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        PageResponse<LoyaltyApi.TransactionResponse> response = call("loyaltyService", false,
                () -> getWithHeader(url, buyerId,
                        new ParameterizedTypeReference<ApiResponse<PageResponse<LoyaltyApi.TransactionResponse>>>() {}),
                "Loyalty service is temporarily unavailable");
        return response.content();
    }

    public List<LoyaltyApi.RewardItemResponse> listLoyaltyRewards() {
        return call("loyaltyService", false,
                () -> get(properties.loyaltyServiceUrl() + LoyaltyApi.REWARDS,
                        new ParameterizedTypeReference<ApiResponse<List<LoyaltyApi.RewardItemResponse>>>() {}),
                "Loyalty service is temporarily unavailable");
    }

    public LoyaltyApi.RedemptionResponse redeemLoyaltyReward(String buyerId, LoyaltyApi.RedeemRequest request) {
        return call("loyaltyService", false,
                () -> postWithHeader(properties.loyaltyServiceUrl() + LoyaltyApi.REDEEM, request, buyerId,
                        new ParameterizedTypeReference<ApiResponse<LoyaltyApi.RedemptionResponse>>() {}),
                "Loyalty service is temporarily unavailable");
    }

    public List<LoyaltyApi.RedemptionResponse> getLoyaltyRedemptions(String buyerId, int page, int size) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.loyaltyServiceUrl() + LoyaltyApi.REDEMPTIONS)
                .queryParam("page", page)
                .queryParam("size", size)
                .toUriString();
        PageResponse<LoyaltyApi.RedemptionResponse> response = call("loyaltyService", false,
                () -> getWithHeader(url, buyerId,
                        new ParameterizedTypeReference<ApiResponse<PageResponse<LoyaltyApi.RedemptionResponse>>>() {}),
                "Loyalty service is temporarily unavailable");
        return response.content();
    }

    public List<LoyaltyApi.OnboardingTaskResponse> getLoyaltyOnboardingTasks(String buyerId) {
        return call("loyaltyService", false,
                () -> getWithHeader(properties.loyaltyServiceUrl() + LoyaltyApi.ONBOARDING_TASKS, buyerId,
                        new ParameterizedTypeReference<ApiResponse<List<LoyaltyApi.OnboardingTaskResponse>>>() {}),
                "Loyalty service is temporarily unavailable");
    }

    public BuyerApi.WelcomeSummaryResponse loadWelcomeSummary(String buyerId) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var accountFuture = executor.submit(() -> getLoyaltyAccount(buyerId));
            var tasksFuture = executor.submit(() -> getLoyaltyOnboardingTasks(buyerId));
            var couponsFuture = executor.submit(() -> post(
                    properties.promotionServiceUrl() + PromotionInternalApi.BUYER_AVAILABLE_COUPONS,
                    new PromotionInternalApi.BuyerCouponsRequest(buyerId),
                    new ParameterizedTypeReference<ApiResponse<PromotionInternalApi.BuyerCouponsResponse>>() {}));
            var inviteFuture = executor.submit(() -> post(
                    properties.profileServiceUrl() + ProfileInternalApi.INVITE_STATS,
                    new ProfileInternalApi.InviteStatsRequest(buyerId),
                    new ParameterizedTypeReference<ApiResponse<ProfileInternalApi.InviteStatsResponse>>() {}));
            return new BuyerApi.WelcomeSummaryResponse(
                    accountFuture.get().balance(),
                    couponsFuture.get().coupons().stream()
                            .map(coupon -> new BuyerApi.WelcomeCouponResponse(coupon.code(), coupon.title(), coupon.expiresAt()))
                            .toList(),
                    tasksFuture.get(),
                    inviteFuture.get().inviteCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Welcome summary interrupted", exception);
        } catch (ExecutionException exception) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Welcome summary aggregation failed", exception);
        }
    }

    public BuyerApi.InviteStatsResponse getInviteStats(String buyerId) {
        ProfileInternalApi.InviteStatsResponse response = post(
                properties.profileServiceUrl() + ProfileInternalApi.INVITE_STATS,
                new ProfileInternalApi.InviteStatsRequest(buyerId),
                new ParameterizedTypeReference<ApiResponse<ProfileInternalApi.InviteStatsResponse>>() {});
        return new BuyerApi.InviteStatsResponse(
                response.inviteCode(),
                response.inviteLink(),
                response.totalInvited(),
                response.totalRewarded(),
                response.monthlyRewardCount(),
                response.monthlyRewardLimit(),
                response.records().stream()
                        .map(record -> new BuyerApi.InviteRecordResponse(
                                record.inviteeNickname(),
                                record.status(),
                                record.registeredAt()))
                        .toList());
    }

    public LoyaltyApi.TransactionResponse loyaltyDeductPoints(String buyerId, long points, String referenceId, String remark) {
        return call("loyaltyService", false,
                () -> post(properties.loyaltyServiceUrl() + LoyaltyApi.INTERNAL_DEDUCT,
                        new LoyaltyApi.DeductPointsRequest(buyerId, "CHECKOUT", points, referenceId, remark),
                        new ParameterizedTypeReference<ApiResponse<LoyaltyApi.TransactionResponse>>() {}),
                "Loyalty service is temporarily unavailable");
    }

    // ── Promotions ──

    public List<PromotionApi.OfferResponse> listPromotions() {
        return resilienceHelper.read("promotionService",
                () -> post(properties.promotionServiceUrl() + PromotionApi.LIST,
                        new PromotionApi.ListOffersRequest(null),
                        new ParameterizedTypeReference<ApiResponse<PromotionApi.OffersView>>() {}).offers(),
                this::listPromotionsFallback);
    }

    public List<PromotionApi.OfferResponse> listPromotionsFallback(Throwable throwable) {
        log.warn("promotion-service unavailable, returning no promotions: {}", throwable.getMessage());
        return List.of();
    }

    public List<PromotionApi.CouponResponse> listCoupons() {
        return resilienceHelper.read("promotionService",
                () -> post(properties.promotionServiceUrl() + PromotionApi.COUPON_LIST,
                        new PromotionApi.ListCouponsRequest(null),
                        new ParameterizedTypeReference<ApiResponse<List<PromotionApi.CouponResponse>>>() {}),
                this::listCouponsFallback);
    }

    public List<PromotionApi.CouponResponse> listCouponsFallback(Throwable throwable) {
        log.warn("promotion-service unavailable, returning no coupons: {}", throwable.getMessage());
        return List.of();
    }

    public PromotionApi.CouponValidationResponse validateCouponForCheckout(String couponCode, BigDecimal orderAmount) {
        return resilienceHelper.read("promotionService",
                () -> post(properties.promotionServiceUrl() + PromotionApi.COUPON_VALIDATE,
                        new PromotionApi.ValidateCouponRequest(couponCode, orderAmount),
                        new ParameterizedTypeReference<ApiResponse<PromotionApi.CouponValidationResponse>>() {}),
                throwable -> validateCouponForCheckoutFallback(couponCode, orderAmount, throwable));
    }

    public PromotionApi.CouponValidationResponse validateCouponForCheckoutFallback(
            String couponCode, BigDecimal orderAmount, Throwable throwable) {
        if (throwable instanceof BusinessException businessException
                && shouldPropagateCouponFailure(businessException)) {
            throw businessException;
        }
        log.warn("promotion-service unavailable during checkout, skipping coupon {}: {}", couponCode, throwable.getMessage());
        return null;
    }

    public boolean deductLoyaltyPointsForCheckout(String buyerId, long points, String referenceId, String remark) {
        return resilienceHelper.write("loyaltyService",
                () -> {
                    loyaltyDeductPoints(buyerId, points, referenceId, remark);
                    return true;
                },
                throwable -> deductLoyaltyPointsForCheckoutFallback(buyerId, points, referenceId, remark, throwable));
    }

    public boolean deductLoyaltyPointsForCheckoutFallback(
            String buyerId, long points, String referenceId, String remark, Throwable throwable) {
        if (throwable instanceof BusinessException businessException
                && shouldPropagateLoyaltyFailure(businessException)) {
            throw businessException;
        }
        log.warn("loyalty-service unavailable during checkout, skipping points deduction for buyer {}: {}",
                buyerId, throwable.getMessage());
        return false;
    }

    // ── Marketplace ──

    public List<MarketplaceApi.ProductResponse> listMarketplace() {
        MarketplaceApi.ProductsView response = call("marketplaceService", true,
                () -> post(properties.marketplaceServiceUrl() + MarketplaceApi.LIST,
                        new MarketplaceApi.ListProductsRequest(true),
                        new ParameterizedTypeReference<ApiResponse<MarketplaceApi.ProductsView>>() {}),
                "Marketplace service is temporarily unavailable");
        return response.products();
    }

    public MarketplaceApi.ProductResponse getProduct(String productId) {
        return call("marketplaceService", true,
                () -> post(properties.marketplaceServiceUrl() + MarketplaceApi.GET,
                        new MarketplaceApi.GetProductRequest(productId),
                        new ParameterizedTypeReference<ApiResponse<MarketplaceApi.ProductResponse>>() {}),
                "Marketplace service is temporarily unavailable");
    }

    public void deductInventoryForCheckout(String productId, int quantity) {
        resilienceHelper.write("marketplaceService",
                () -> postVoid(properties.marketplaceServiceUrl() + MarketplaceApi.INVENTORY_DEDUCT,
                        new MarketplaceApi.DeductInventoryRequest(productId, quantity)),
                throwable -> {
                    deductInventoryForCheckoutFallback(productId, quantity, throwable);
                    return null;
                });
    }

    public void deductInventoryForCheckoutFallback(String productId, int quantity, Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            throw businessException;
        }
        throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR,
                "Marketplace inventory is temporarily unavailable", throwable);
    }

    // ── Seller Storefront ──

    public ProfileApi.SellerStorefrontResponse getSellerStorefront(String sellerId) {
        return call("profileService", true,
                () -> post(properties.profileServiceUrl() + ProfileApi.SELLER_STOREFRONT,
                        new ProfileApi.GetProfileRequest(sellerId),
                        new ParameterizedTypeReference<ApiResponse<ProfileApi.SellerStorefrontResponse>>() {}),
                "Profile service is temporarily unavailable");
    }

    public SearchApi.SearchProductsResponse searchProducts(MarketplaceApi.SearchProductsRequest request) {
        String uri = UriComponentsBuilder.fromPath(SearchApi.SEARCH_PRODUCTS)
                .queryParamIfPresent("q", Optional.ofNullable(request.query()))
                .queryParamIfPresent("categoryId", Optional.ofNullable(request.categoryId()))
                .queryParam("page", request.page())
                .queryParam("hitsPerPage", request.size())
                .build()
                .toUriString();
        return resilienceHelper.read("searchService",
                () -> {
                    try {
                        ApiResponse<SearchApi.SearchProductsResponse> response = searchServiceClient.searchProducts(
                                request.query(),
                                request.categoryId(),
                                request.page(),
                                request.size());
                        if (response == null || response.data() == null) {
                            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR,
                                    "Empty downstream response from " + uri);
                        }
                        return response.data();
                    } catch (RestClientResponseException exception) {
                        throw translateDownstreamException(uri, exception);
                    }
                },
                throwable -> searchProductsFallback(request, throwable));
    }

    public SearchApi.SearchProductsResponse searchProductsFallback(MarketplaceApi.SearchProductsRequest request, Throwable throwable) {
        log.warn("search-service unavailable, fallback to marketplace search: {}", throwable.getMessage());
        MarketplaceApi.ProductsPageView fallback = post(
                properties.marketplaceServiceUrl() + MarketplaceApi.SEARCH,
                request,
                new ParameterizedTypeReference<ApiResponse<MarketplaceApi.ProductsPageView>>() {});
        List<SearchApi.ProductHit> hits = fallback.products().stream()
                .map(product -> new SearchApi.ProductHit(
                        product.id().toString(),
                        product.sellerId(),
                        product.name(),
                        product.description(),
                        product.price(),
                        product.inventory() == null ? 0 : product.inventory(),
                        product.categoryId(),
                        product.categoryName(),
                        product.imageUrl(),
                        product.status()))
                .toList();
        int totalPages = fallback.size() <= 0 ? 0 : (int) Math.ceil((double) fallback.total() / fallback.size());
        return new SearchApi.SearchProductsResponse(
                hits,
                fallback.total(),
                fallback.page(),
                totalPages,
                Map.of());
    }

    public List<MarketplaceApi.CategoryResponse> listCategories() {
        return call("marketplaceService", true,
                () -> post(properties.marketplaceServiceUrl() + MarketplaceApi.CATEGORY_LIST, Map.of(),
                        new ParameterizedTypeReference<ApiResponse<List<MarketplaceApi.CategoryResponse>>>() {}),
                "Marketplace service is temporarily unavailable");
    }

    // ── Cart ──

    public OrderApi.CartView listCart(String buyerId) {
        if (guestCartStore.isGuestBuyer(buyerId)) {
            return guestCartStore.listCart(buyerId);
        }
        return call("orderService", false,
                () -> post(properties.orderServiceUrl() + OrderApi.CART_LIST,
                        new OrderApi.ListCartRequest(buyerId),
                        new ParameterizedTypeReference<ApiResponse<OrderApi.CartView>>() {}),
                "Order service is temporarily unavailable");
    }

    public OrderApi.CartItemResponse addToCart(OrderApi.AddToCartRequest request) {
        if (guestCartStore.isGuestBuyer(request.buyerId())) {
            return guestCartStore.addToCart(request);
        }
        return call("orderService", false,
                () -> post(properties.orderServiceUrl() + OrderApi.CART_ADD, request,
                        new ParameterizedTypeReference<ApiResponse<OrderApi.CartItemResponse>>() {}),
                "Order service is temporarily unavailable");
    }

    public OrderApi.CartItemResponse updateCart(OrderApi.UpdateCartRequest request) {
        if (guestCartStore.isGuestBuyer(request.buyerId())) {
            return guestCartStore.updateCart(request);
        }
        return call("orderService", false,
                () -> post(properties.orderServiceUrl() + OrderApi.CART_UPDATE, request,
                        new ParameterizedTypeReference<ApiResponse<OrderApi.CartItemResponse>>() {}),
                "Order service is temporarily unavailable");
    }

    public void removeFromCart(OrderApi.RemoveFromCartRequest request) {
        if (guestCartStore.isGuestBuyer(request.buyerId())) {
            guestCartStore.removeFromCart(request);
            return;
        }
        call("orderService", false,
                () -> {
                    postVoid(properties.orderServiceUrl() + OrderApi.CART_REMOVE, request);
                    return null;
                },
                "Order service is temporarily unavailable");
    }

    public OrderApi.CartView mergeGuestCart(String buyerId, String guestBuyerId) {
        if (!guestCartStore.isGuestBuyer(guestBuyerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "Cart merge requires a guest buyer source cart");
        }
        OrderApi.CartView guestCart = guestCartStore.listCart(guestBuyerId);
        for (OrderApi.CartItemResponse item : guestCart.items()) {
            call("orderService", false,
                    () -> post(properties.orderServiceUrl() + OrderApi.CART_ADD,
                            new OrderApi.AddToCartRequest(
                                    buyerId,
                                    item.productId(),
                                    item.productName(),
                                    item.productPrice(),
                                    item.sellerId(),
                                    item.quantity()),
                            new ParameterizedTypeReference<ApiResponse<OrderApi.CartItemResponse>>() {}),
                    "Order service is temporarily unavailable");
            guestCartStore.removeFromCart(new OrderApi.RemoveFromCartRequest(guestBuyerId, item.productId()));
        }
        return listCart(buyerId);
    }

    // ── Orders ──

    public List<OrderApi.OrderResponse> listOrders(String buyerId, String role) {
        return call("orderService", false,
                () -> post(properties.orderServiceUrl() + OrderApi.ORDER_LIST,
                        new OrderApi.ListOrdersRequest(buyerId, role),
                        new ParameterizedTypeReference<ApiResponse<List<OrderApi.OrderResponse>>>() {}),
                "Order service is temporarily unavailable");
    }

    public OrderApi.OrderResponse getOrder(String orderId) {
        return call("orderService", false,
                () -> post(properties.orderServiceUrl() + OrderApi.ORDER_GET,
                        new OrderApi.GetOrderRequest(orderId),
                        new ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {}),
                "Order service is temporarily unavailable");
    }

    public OrderApi.OrderResponse cancelOrder(String orderId, String buyerId) {
        OrderApi.OrderResponse order = getOrder(orderId);
        if ("PAID".equals(order.status())) {
            try {
                call("walletService", false,
                        () -> post(properties.walletServiceUrl() + WalletApi.PAYMENT_REFUND,
                                new WalletApi.CreateRefundRequest(buyerId, order.totalAmount(), "usd", orderId, "ORDER"),
                                new ParameterizedTypeReference<ApiResponse<WalletApi.TransactionResponse>>() {}),
                        "Wallet service is temporarily unavailable");
            } catch (BusinessException | RestClientException exception) {
                log.error("Refund failed for order {}: {}", orderId, exception.getMessage());
            }
        }
        return call("orderService", false,
                () -> post(properties.orderServiceUrl() + OrderApi.ORDER_CANCEL,
                        new OrderApi.CancelOrderRequest(orderId, "User cancelled"),
                        new ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {}),
                "Order service is temporarily unavailable");
    }

    // ── Checkout Orchestration ──

    public BuyerApi.CheckoutResponse checkout(BuyerApi.CheckoutRequest request) {
        String buyerId = request.buyerId();
        String paymentMethod = request.paymentMethod() != null ? request.paymentMethod() : "WALLET";

        // 1. Fetch cart
        OrderApi.CartView cart = listCart(buyerId);
        if (cart.items().isEmpty()) {
            throw new BusinessException(CommonErrorCode.CART_EMPTY, "Cart is empty");
        }

        // 2. Validate coupon if provided
        PromotionApi.CouponValidationResponse couponValidation = null;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            couponValidation = validateCouponForCheckout(request.couponCode(), cart.subtotal());
            if (couponValidation != null && !couponValidation.valid()) {
                throw new BusinessException(CommonErrorCode.COUPON_INVALID, couponValidation.message());
            }
        }

        // 2b. Calculate points discount (max 20% of subtotal)
        BigDecimal couponDiscount = couponValidation != null ? couponValidation.discountAmount() : BigDecimal.ZERO;
        BigDecimal afterCoupon = cart.subtotal().subtract(couponDiscount).max(BigDecimal.ZERO);
        BigDecimal pointsDiscount = BigDecimal.ZERO;
        long pointsUsed = 0;
        if (request.pointsToUse() != null && request.pointsToUse() > 0) {
            BigDecimal maxPointsDiscount = afterCoupon.multiply(new BigDecimal("0.20"));
            // 100 points = $1
            BigDecimal requestedDiscount = BigDecimal.valueOf(request.pointsToUse()).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal requestedPointsDiscount = requestedDiscount.min(maxPointsDiscount);
            long requestedPoints = requestedPointsDiscount.multiply(new BigDecimal("100")).longValue();
            if (requestedPoints > 0 && deductLoyaltyPointsForCheckout(
                    buyerId,
                    requestedPoints,
                    "checkout-" + buyerId + "-" + System.currentTimeMillis(),
                    "Points discount for checkout")) {
                pointsDiscount = requestedPointsDiscount;
                pointsUsed = requestedPoints;
            }
        }
        PromotionApi.CouponValidationResponse appliedCouponValidation = couponValidation;

        // 3. Group items by seller
        Map<String, List<OrderApi.CartItemResponse>> itemsBySeller = cart.items().stream()
                .collect(Collectors.groupingBy(OrderApi.CartItemResponse::sellerId));

        List<MarketplaceApi.DeductInventoryRequest> deductedInventory = new ArrayList<>();
        List<OrderApi.OrderResponse> createdOrders = new ArrayList<>();
        BigDecimal totalPaid = BigDecimal.ZERO;
        String lastClientSecret = null;
        String lastRedirectUrl = null;

        try {
            // 4. Deduct inventory for all items
            for (OrderApi.CartItemResponse item : cart.items()) {
                deductInventoryForCheckout(item.productId(), item.quantity());
                deductedInventory.add(new MarketplaceApi.DeductInventoryRequest(item.productId(), item.quantity()));
            }

            // 5. Calculate discount distribution (coupon + points)
            BigDecimal totalDiscount = couponDiscount.add(pointsDiscount);
            int sellerCount = itemsBySeller.size();

            // 6. Create order for each seller group
            for (Map.Entry<String, List<OrderApi.CartItemResponse>> entry : itemsBySeller.entrySet()) {
                String sellerId = entry.getKey();
                List<OrderApi.CartItemResponse> sellerItems = entry.getValue();

                BigDecimal subtotal = sellerItems.stream()
                        .map(i -> i.productPrice().multiply(BigDecimal.valueOf(i.quantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal orderDiscount = sellerCount == 1 ? totalDiscount :
                        totalDiscount.multiply(subtotal).divide(cart.subtotal(), 2, java.math.RoundingMode.HALF_UP);
                BigDecimal orderTotal = subtotal.subtract(orderDiscount).max(BigDecimal.ZERO);

                String paymentTransactionId;
                String clientSecret = null;

                if ("WALLET".equals(paymentMethod)) {
                    // Pay from wallet
                    WalletApi.TransactionResponse payment = call("walletService", false,
                            () -> post(properties.walletServiceUrl() + WalletApi.PAYMENT_CREATE,
                                    new WalletApi.CreatePaymentRequest(buyerId, orderTotal, "usd", "checkout", "ORDER"),
                                    new ParameterizedTypeReference<ApiResponse<WalletApi.TransactionResponse>>() {}),
                            "Wallet service is temporarily unavailable");
                    paymentTransactionId = payment.transactionId();
                } else {
                    // Create payment intent (Stripe/PayPal) — frontend completes payment
                    WalletApi.PaymentIntentResponse intent = call("walletService", false,
                            () -> post(properties.walletServiceUrl() + WalletApi.PAYMENT_INTENT,
                                    new WalletApi.CreatePaymentIntentRequest(buyerId, orderTotal, "usd", paymentMethod),
                                    new ParameterizedTypeReference<ApiResponse<WalletApi.PaymentIntentResponse>>() {}),
                            "Wallet service is temporarily unavailable");
                    paymentTransactionId = intent.intentId();
                    clientSecret = intent.clientSecret();
                    lastClientSecret = clientSecret;
                    lastRedirectUrl = intent.redirectUrl();
                }

                // Create order
                List<OrderApi.CreateOrderItemRequest> orderItems = sellerItems.stream()
                        .map(i -> new OrderApi.CreateOrderItemRequest(i.productId(), i.productName(),
                                i.productPrice(), i.quantity(),
                                i.productPrice().multiply(BigDecimal.valueOf(i.quantity()))))
                        .toList();

                OrderApi.OrderResponse order = call("orderService", false,
                        () -> post(properties.orderServiceUrl() + OrderApi.CHECKOUT_CREATE,
                                new OrderApi.CreateOrderRequest(buyerId, sellerId, subtotal, orderDiscount, orderTotal,
                                        appliedCouponValidation != null ? appliedCouponValidation.couponId() : null,
                                        appliedCouponValidation != null ? appliedCouponValidation.code() : null,
                                        paymentTransactionId, orderItems),
                                new ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {}),
                        "Order service is temporarily unavailable");

                createdOrders.add(order);
                totalPaid = totalPaid.add(orderTotal);

                // Record coupon usage per order
                if (appliedCouponValidation != null) {
                    try {
                        call("promotionService", false,
                                () -> post(properties.promotionServiceUrl() + PromotionApi.COUPON_APPLY,
                                        new PromotionApi.ApplyCouponRequest(appliedCouponValidation.couponId(), buyerId,
                                                order.id().toString(), orderDiscount),
                                        new ParameterizedTypeReference<ApiResponse<Void>>() {}),
                                "Promotion service is temporarily unavailable");
                    } catch (BusinessException | RestClientException exception) {
                        log.warn("Failed to record coupon usage for order {}: {}", order.id(), exception.getMessage());
                    }
                }
            }

            // 8. Clear cart
            clearCart(buyerId);

        } catch (RuntimeException exception) {
            // Compensate: restore inventory
            for (MarketplaceApi.DeductInventoryRequest deducted : deductedInventory) {
                try {
                    postVoid(properties.marketplaceServiceUrl() + MarketplaceApi.INVENTORY_RESTORE,
                            new MarketplaceApi.RestoreInventoryRequest(deducted.productId(), deducted.quantity()));
                } catch (BusinessException | RestClientException compensationException) {
                    log.error("Failed to restore inventory for product {}: {}",
                            deducted.productId(), compensationException.getMessage());
                }
            }
            // Compensate: refund wallet payments
            for (OrderApi.OrderResponse order : createdOrders) {
                try {
                    post(properties.walletServiceUrl() + WalletApi.PAYMENT_REFUND,
                            new WalletApi.CreateRefundRequest(buyerId, order.totalAmount(), "usd",
                                    order.id().toString(), "ORDER"),
                            new ParameterizedTypeReference<ApiResponse<WalletApi.TransactionResponse>>() {});
                } catch (BusinessException | RestClientException compensationException) {
                    log.error("Failed to refund for order {}: {}", order.id(), compensationException.getMessage());
                }
            }
            // Compensate: refund loyalty points
            if (pointsUsed > 0) {
                try {
                    post(properties.loyaltyServiceUrl() + LoyaltyApi.INTERNAL_EARN,
                            new LoyaltyApi.EarnPointsRequest(buyerId, "CHECKOUT_REFUND", pointsUsed,
                                    "checkout-refund-" + buyerId + "-" + System.currentTimeMillis(),
                                    "Points refund due to checkout failure"),
                            new ParameterizedTypeReference<ApiResponse<LoyaltyApi.TransactionResponse>>() {});
                } catch (BusinessException | RestClientException compensationException) {
                    log.error("Failed to refund loyalty points for buyer {}: {}",
                            buyerId, compensationException.getMessage());
                }
            }
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "Checkout failed: " + exception.getMessage(),
                    exception);
        }

        return new BuyerApi.CheckoutResponse(createdOrders, totalPaid, paymentMethod, lastClientSecret, lastRedirectUrl);
    }

    // ── Guest Shopping ──

    public OrderApi.OrderResponse guestCheckout(OrderApi.GuestCheckoutRequest request) {
        return call("orderService", false,
                () -> post(properties.orderServiceUrl() + OrderApi.GUEST_CHECKOUT, request,
                        new ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {}),
                "Order service is temporarily unavailable");
    }

    public OrderApi.OrderResponse trackOrder(String orderToken) {
        return call("orderService", false,
                () -> get(properties.orderServiceUrl() + OrderApi.ORDER_TRACK + "?token=" + orderToken,
                        new ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {}),
                "Order service is temporarily unavailable");
    }

    private <T> T call(String instanceName, boolean retryEnabled, Supplier<T> supplier, String unavailableMessage) {
        if (retryEnabled) {
            return resilienceHelper.read(instanceName, supplier, throwable -> failDownstream(unavailableMessage, throwable));
        }
        return resilienceHelper.write(instanceName, supplier, throwable -> failDownstream(unavailableMessage, throwable));
    }

    private <T> T failDownstream(String message, Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            throw businessException;
        }
        throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, message, throwable);
    }

    private void clearCart(String buyerId) {
        if (guestCartStore.isGuestBuyer(buyerId)) {
            guestCartStore.clearCart(buyerId);
            return;
        }
        call("orderService", false,
                () -> {
                    postVoid(properties.orderServiceUrl() + OrderApi.CART_CLEAR,
                            new OrderApi.ClearCartRequest(buyerId));
                    return null;
                },
                "Order service is temporarily unavailable");
    }

    private <T> T post(String url, Object request, ParameterizedTypeReference<ApiResponse<T>> typeReference) {
        try {
            ApiResponse<T> response = restClient.post()
                    .uri(url)
                    .header(TrustedHeaderNames.INTERNAL_TOKEN, properties.internalToken())
                    .body(request)
                    .retrieve()
                    .body(typeReference);
            if (response == null || response.data() == null) {
                throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Empty downstream response from " + url);
            }
            return response.data();
        } catch (RestClientResponseException exception) {
            throw translateDownstreamException(url, exception);
        }
    }

    private void postVoid(String url, Object request) {
        try {
            ApiResponse<?> response = restClient.post()
                    .uri(url)
                    .header(TrustedHeaderNames.INTERNAL_TOKEN, properties.internalToken())
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<Void>>() {});
            if (response == null) {
                throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Empty downstream response from " + url);
            }
        } catch (RestClientResponseException exception) {
            throw translateDownstreamException(url, exception);
        }
    }

    private <T> T get(String url, ParameterizedTypeReference<ApiResponse<T>> typeReference) {
        try {
            ApiResponse<T> response = restClient.get()
                    .uri(url)
                    .header(TrustedHeaderNames.INTERNAL_TOKEN, properties.internalToken())
                    .retrieve()
                    .body(typeReference);
            if (response == null || response.data() == null) {
                throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Empty downstream response from " + url);
            }
            return response.data();
        } catch (RestClientResponseException exception) {
            throw translateDownstreamException(url, exception);
        }
    }

    private <T> T getWithHeader(String url, String buyerId,
                                  ParameterizedTypeReference<ApiResponse<T>> typeReference) {
        try {
            ApiResponse<T> response = restClient.get()
                    .uri(url)
                    .header(TrustedHeaderNames.INTERNAL_TOKEN, properties.internalToken())
                    .header(TrustedHeaderNames.BUYER_ID, buyerId)
                    .retrieve()
                    .body(typeReference);
            if (response == null || response.data() == null) {
                throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Empty downstream response from " + url);
            }
            return response.data();
        } catch (RestClientResponseException exception) {
            throw translateDownstreamException(url, exception);
        }
    }

    private <T> T postWithHeader(String url, Object request, String buyerId,
                                   ParameterizedTypeReference<ApiResponse<T>> typeReference) {
        try {
            ApiResponse<T> response = restClient.post()
                    .uri(url)
                    .header(TrustedHeaderNames.INTERNAL_TOKEN, properties.internalToken())
                    .header(TrustedHeaderNames.BUYER_ID, buyerId)
                    .body(request)
                    .retrieve()
                    .body(typeReference);
            if (response == null || response.data() == null) {
                throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Empty downstream response from " + url);
            }
            return response.data();
        } catch (RestClientResponseException exception) {
            throw translateDownstreamException(url, exception);
        }
    }

    private BusinessException translateDownstreamException(String url, RestClientResponseException exception) {
        CommonErrorCode errorCode = CommonErrorCode.DOWNSTREAM_ERROR;
        String message = "Downstream request failed: " + url;
        byte[] responseBody = exception.getResponseBodyAsByteArray();
        if (responseBody != null && responseBody.length > 0) {
            try {
                JsonNode errorBody = objectMapper.readTree(responseBody);
                String code = errorBody.path("code").asText();
                String status = code.isBlank() ? errorBody.path("status").asText() : code;
                String downstreamMessage = errorBody.path("message").asText();
                if (downstreamMessage.isBlank()) {
                    downstreamMessage = errorBody.path("detail").asText();
                }
                if (!status.isBlank()) {
                    errorCode = resolveCommonErrorCode(status);
                }
                if (!downstreamMessage.isBlank()) {
                    message = downstreamMessage;
                }
            } catch (IOException parsingException) {
                message = exception.getMessage();
            }
        } else if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            message = exception.getMessage();
        }
        return new BusinessException(errorCode, message, exception);
    }

    private CommonErrorCode resolveCommonErrorCode(String code) {
        for (CommonErrorCode errorCode : CommonErrorCode.values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return CommonErrorCode.DOWNSTREAM_ERROR;
    }

    private boolean shouldPropagateCouponFailure(BusinessException exception) {
        return CommonErrorCode.COUPON_INVALID.equals(exception.getErrorCode())
                || CommonErrorCode.COUPON_EXPIRED.equals(exception.getErrorCode())
                || CommonErrorCode.VALIDATION_ERROR.equals(exception.getErrorCode());
    }

    private boolean shouldPropagateLoyaltyFailure(BusinessException exception) {
        return CommonErrorCode.INSUFFICIENT_BALANCE.equals(exception.getErrorCode())
                || CommonErrorCode.VALIDATION_ERROR.equals(exception.getErrorCode());
    }
}
