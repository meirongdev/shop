package dev.meirong.shop.buyerbff.service;

import dev.meirong.shop.clients.loyalty.LoyaltyServiceClient;
import dev.meirong.shop.clients.PageResponse;
import dev.meirong.shop.clients.marketplace.MarketplaceServiceClient;
import dev.meirong.shop.clients.order.OrderServiceClient;
import dev.meirong.shop.clients.profile.ProfileInternalServiceClient;
import dev.meirong.shop.clients.profile.ProfileServiceClient;
import dev.meirong.shop.clients.promotion.PromotionInternalServiceClient;
import dev.meirong.shop.clients.promotion.PromotionServiceClient;
import dev.meirong.shop.clients.search.SearchServiceClient;
import dev.meirong.shop.clients.wallet.WalletServiceClient;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.resilience.ResilienceHelper;
import dev.meirong.shop.contracts.buyer.BuyerApi;
import dev.meirong.shop.contracts.loyalty.LoyaltyApi;
import dev.meirong.shop.contracts.marketplace.MarketplaceApi;
import dev.meirong.shop.contracts.order.OrderApi;
import dev.meirong.shop.contracts.profile.ProfileApi;
import dev.meirong.shop.contracts.profile.ProfileInternalApi;
import dev.meirong.shop.contracts.promotion.PromotionApi;
import dev.meirong.shop.contracts.promotion.PromotionInternalApi;
import dev.meirong.shop.contracts.search.SearchApi;
import dev.meirong.shop.contracts.wallet.WalletApi;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class BuyerAggregationService {

    private static final Logger log = LoggerFactory.getLogger(BuyerAggregationService.class);

    private final ProfileServiceClient profileServiceClient;
    private final ProfileInternalServiceClient profileInternalServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final PromotionServiceClient promotionServiceClient;
    private final PromotionInternalServiceClient promotionInternalServiceClient;
    private final MarketplaceServiceClient marketplaceServiceClient;
    private final OrderServiceClient orderServiceClient;
    private final LoyaltyServiceClient loyaltyServiceClient;
    private final SearchServiceClient searchServiceClient;
    private final ResilienceHelper resilienceHelper;
    private final GuestCartStore guestCartStore;

    public BuyerAggregationService(ProfileServiceClient profileServiceClient,
                                    ProfileInternalServiceClient profileInternalServiceClient,
                                    WalletServiceClient walletServiceClient,
                                    PromotionServiceClient promotionServiceClient,
                                    PromotionInternalServiceClient promotionInternalServiceClient,
                                    MarketplaceServiceClient marketplaceServiceClient,
                                    OrderServiceClient orderServiceClient,
                                    LoyaltyServiceClient loyaltyServiceClient,
                                    SearchServiceClient searchServiceClient,
                                    ResilienceHelper resilienceHelper,
                                    GuestCartStore guestCartStore) {
        this.profileServiceClient = profileServiceClient;
        this.profileInternalServiceClient = profileInternalServiceClient;
        this.walletServiceClient = walletServiceClient;
        this.promotionServiceClient = promotionServiceClient;
        this.promotionInternalServiceClient = promotionInternalServiceClient;
        this.marketplaceServiceClient = marketplaceServiceClient;
        this.orderServiceClient = orderServiceClient;
        this.loyaltyServiceClient = loyaltyServiceClient;
        this.searchServiceClient = searchServiceClient;
        this.resilienceHelper = resilienceHelper;
        this.guestCartStore = guestCartStore;
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
                () -> requireData(profileServiceClient.getProfile(new ProfileApi.GetProfileRequest(buyerId))),
                "Profile service is temporarily unavailable");
    }

    public ProfileApi.ProfileResponse updateProfile(ProfileApi.UpdateProfileRequest request) {
        return call("profileService", false,
                () -> requireData(profileServiceClient.updateProfile(request)),
                "Profile service is temporarily unavailable");
    }

    // ── Wallet ──

    public WalletApi.WalletAccountResponse getWallet(String buyerId) {
        return call("walletService", false,
                () -> requireData(walletServiceClient.getWallet(new WalletApi.GetWalletRequest(buyerId))),
                "Wallet service is temporarily unavailable");
    }

    public WalletApi.TransactionResponse deposit(WalletApi.DepositRequest request) {
        return call("walletService", false,
                () -> requireData(walletServiceClient.deposit(request)),
                "Wallet service is temporarily unavailable");
    }

    public WalletApi.TransactionResponse withdraw(WalletApi.WithdrawRequest request) {
        return call("walletService", false,
                () -> requireData(walletServiceClient.withdraw(request)),
                "Wallet service is temporarily unavailable");
    }

    public List<WalletApi.PaymentMethodInfo> listPaymentMethods() {
        return call("walletService", false,
                () -> requireData(walletServiceClient.listPaymentMethods()),
                "Wallet service is temporarily unavailable");
    }

    public WalletApi.PaymentIntentResponse createPaymentIntent(WalletApi.CreatePaymentIntentRequest request) {
        return call("walletService", false,
                () -> requireData(walletServiceClient.createPaymentIntent(request)),
                "Wallet service is temporarily unavailable");
    }

    // ── Loyalty ──

    public LoyaltyApi.AccountResponse getLoyaltyAccount(String buyerId) {
        return call("loyaltyService", false,
                () -> requireData(loyaltyServiceClient.getAccount(buyerId)),
                "Loyalty service is temporarily unavailable");
    }

    public LoyaltyApi.CheckinResponse loyaltyCheckin(String buyerId) {
        return call("loyaltyService", false,
                () -> requireData(loyaltyServiceClient.checkin(buyerId)),
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
        return call("loyaltyService", false,
                () -> requireData(loyaltyServiceClient.getCheckinCalendar(buyerId, year, month)),
                "Loyalty service is temporarily unavailable");
    }

    public List<LoyaltyApi.TransactionResponse> getLoyaltyTransactions(String buyerId, int page, int size) {
        PageResponse<LoyaltyApi.TransactionResponse> response = call("loyaltyService", false,
                () -> requireData(loyaltyServiceClient.getTransactions(buyerId, page, size)),
                "Loyalty service is temporarily unavailable");
        return response.content();
    }

    public List<LoyaltyApi.RewardItemResponse> listLoyaltyRewards() {
        return call("loyaltyService", false,
                () -> requireData(loyaltyServiceClient.listRewards()),
                "Loyalty service is temporarily unavailable");
    }

    public LoyaltyApi.RedemptionResponse redeemLoyaltyReward(String buyerId, LoyaltyApi.RedeemRequest request) {
        return call("loyaltyService", false,
                () -> requireData(loyaltyServiceClient.redeemReward(buyerId, request)),
                "Loyalty service is temporarily unavailable");
    }

    public List<LoyaltyApi.RedemptionResponse> getLoyaltyRedemptions(String buyerId, int page, int size) {
        PageResponse<LoyaltyApi.RedemptionResponse> response = call("loyaltyService", false,
                () -> requireData(loyaltyServiceClient.getRedemptions(buyerId, page, size)),
                "Loyalty service is temporarily unavailable");
        return response.content();
    }

    public List<LoyaltyApi.OnboardingTaskResponse> getLoyaltyOnboardingTasks(String buyerId) {
        return call("loyaltyService", false,
                () -> requireData(loyaltyServiceClient.getOnboardingTasks(buyerId)),
                "Loyalty service is temporarily unavailable");
    }

    public BuyerApi.WelcomeSummaryResponse loadWelcomeSummary(String buyerId) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var accountFuture = executor.submit(() -> getLoyaltyAccount(buyerId));
            var tasksFuture = executor.submit(() -> getLoyaltyOnboardingTasks(buyerId));
            var couponsFuture = executor.submit(() -> requireData(
                    promotionInternalServiceClient.getBuyerAvailableCoupons(
                            new PromotionInternalApi.BuyerCouponsRequest(buyerId))));
            var inviteFuture = executor.submit(() -> requireData(
                    profileInternalServiceClient.getInviteStats(
                            new ProfileInternalApi.InviteStatsRequest(buyerId))));
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
        ProfileInternalApi.InviteStatsResponse response = requireData(
                profileInternalServiceClient.getInviteStats(
                        new ProfileInternalApi.InviteStatsRequest(buyerId)));
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
                () -> requireData(loyaltyServiceClient.deductPoints(
                        new LoyaltyApi.DeductPointsRequest(buyerId, "CHECKOUT", points, referenceId, remark))),
                "Loyalty service is temporarily unavailable");
    }

    // ── Promotions ──

    public List<PromotionApi.OfferResponse> listPromotions() {
        return resilienceHelper.read("promotionService",
                () -> requireData(promotionServiceClient.listOffers(
                        new PromotionApi.ListOffersRequest(null))).offers(),
                this::listPromotionsFallback);
    }

    public List<PromotionApi.OfferResponse> listPromotionsFallback(Throwable throwable) {
        log.warn("promotion-service unavailable, returning no promotions: {}", throwable.getMessage());
        return List.of();
    }

    public List<PromotionApi.CouponResponse> listCoupons() {
        return resilienceHelper.read("promotionService",
                () -> requireData(promotionServiceClient.listCoupons(
                        new PromotionApi.ListCouponsRequest(null))),
                this::listCouponsFallback);
    }

    public List<PromotionApi.CouponResponse> listCouponsFallback(Throwable throwable) {
        log.warn("promotion-service unavailable, returning no coupons: {}", throwable.getMessage());
        return List.of();
    }

    public PromotionApi.CouponValidationResponse validateCouponForCheckout(String couponCode, BigDecimal orderAmount) {
        return resilienceHelper.read("promotionService",
                () -> requireData(promotionServiceClient.validateCoupon(
                        new PromotionApi.ValidateCouponRequest(couponCode, orderAmount))),
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
                () -> requireData(marketplaceServiceClient.listProducts(
                        new MarketplaceApi.ListProductsRequest(true))),
                "Marketplace service is temporarily unavailable");
        return response.products();
    }

    public MarketplaceApi.ProductResponse getProduct(String productId) {
        return call("marketplaceService", true,
                () -> requireData(marketplaceServiceClient.getProduct(
                        new MarketplaceApi.GetProductRequest(productId))),
                "Marketplace service is temporarily unavailable");
    }

    public void deductInventoryForCheckout(String productId, int quantity) {
        resilienceHelper.write("marketplaceService",
                () -> {
                    marketplaceServiceClient.deductInventory(
                            new MarketplaceApi.DeductInventoryRequest(productId, quantity));
                    return null;
                },
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
                () -> requireData(profileServiceClient.getSellerStorefront(
                        new ProfileApi.GetProfileRequest(sellerId))),
                "Profile service is temporarily unavailable");
    }

    public SearchApi.SearchProductsResponse searchProducts(MarketplaceApi.SearchProductsRequest request) {
        return resilienceHelper.read("searchService",
                () -> requireData(searchServiceClient.searchProducts(
                        request.query(), request.categoryId(), request.page(), request.size())),
                throwable -> searchProductsFallback(request, throwable));
    }

    public SearchApi.SearchProductsResponse searchProductsFallback(MarketplaceApi.SearchProductsRequest request, Throwable throwable) {
        log.warn("search-service unavailable, fallback to marketplace search: {}", throwable.getMessage());
        MarketplaceApi.ProductsPageView fallback = requireData(
                marketplaceServiceClient.searchProducts(request));
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
                () -> requireData(marketplaceServiceClient.listCategories()),
                "Marketplace service is temporarily unavailable");
    }

    // ── Cart ──

    public OrderApi.CartView listCart(String buyerId) {
        if (guestCartStore.isGuestBuyer(buyerId)) {
            return guestCartStore.listCart(buyerId);
        }
        return call("orderService", false,
                () -> requireData(orderServiceClient.listCart(
                        new OrderApi.ListCartRequest(buyerId))),
                "Order service is temporarily unavailable");
    }

    public OrderApi.CartItemResponse addToCart(OrderApi.AddToCartRequest request) {
        if (guestCartStore.isGuestBuyer(request.buyerId())) {
            return guestCartStore.addToCart(request);
        }
        return call("orderService", false,
                () -> requireData(orderServiceClient.addToCart(request)),
                "Order service is temporarily unavailable");
    }

    public OrderApi.CartItemResponse updateCart(OrderApi.UpdateCartRequest request) {
        if (guestCartStore.isGuestBuyer(request.buyerId())) {
            return guestCartStore.updateCart(request);
        }
        return call("orderService", false,
                () -> requireData(orderServiceClient.updateCart(request)),
                "Order service is temporarily unavailable");
    }

    public void removeFromCart(OrderApi.RemoveFromCartRequest request) {
        if (guestCartStore.isGuestBuyer(request.buyerId())) {
            guestCartStore.removeFromCart(request);
            return;
        }
        call("orderService", false,
                () -> {
                    orderServiceClient.removeFromCart(request);
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
                    () -> requireData(orderServiceClient.addToCart(
                            new OrderApi.AddToCartRequest(
                                    buyerId,
                                    item.productId(),
                                    item.productName(),
                                    item.productPrice(),
                                    item.sellerId(),
                                    item.quantity()))),
                    "Order service is temporarily unavailable");
            guestCartStore.removeFromCart(new OrderApi.RemoveFromCartRequest(guestBuyerId, item.productId()));
        }
        return listCart(buyerId);
    }

    // ── Orders ──

    public List<OrderApi.OrderResponse> listOrders(String buyerId, String role) {
        return call("orderService", false,
                () -> requireData(orderServiceClient.listOrders(
                        new OrderApi.ListOrdersRequest(buyerId, role))),
                "Order service is temporarily unavailable");
    }

    public OrderApi.OrderResponse getOrder(String orderId) {
        return call("orderService", false,
                () -> requireData(orderServiceClient.getOrder(
                        new OrderApi.GetOrderRequest(orderId))),
                "Order service is temporarily unavailable");
    }

    public OrderApi.OrderResponse cancelOrder(String orderId, String buyerId) {
        OrderApi.OrderResponse order = getOrder(orderId);
        if ("PAID".equals(order.status())) {
            try {
                call("walletService", false,
                        () -> requireData(walletServiceClient.createRefund(
                                new WalletApi.CreateRefundRequest(buyerId, order.totalAmount(), "usd", orderId, "ORDER"))),
                        "Wallet service is temporarily unavailable");
            } catch (BusinessException | RestClientException exception) {
                log.error("Refund failed for order {}: {}", orderId, exception.getMessage());
            }
        }
        return call("orderService", false,
                () -> requireData(orderServiceClient.cancelOrder(
                        new OrderApi.CancelOrderRequest(orderId, "User cancelled"))),
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
                    WalletApi.TransactionResponse payment = call("walletService", false,
                            () -> requireData(walletServiceClient.createPayment(
                                    new WalletApi.CreatePaymentRequest(buyerId, orderTotal, "usd", "checkout", "ORDER"))),
                            "Wallet service is temporarily unavailable");
                    paymentTransactionId = payment.transactionId();
                } else {
                    WalletApi.PaymentIntentResponse intent = call("walletService", false,
                            () -> requireData(walletServiceClient.createPaymentIntent(
                                    new WalletApi.CreatePaymentIntentRequest(buyerId, orderTotal, "usd", paymentMethod))),
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
                        () -> requireData(orderServiceClient.createOrder(
                                new OrderApi.CreateOrderRequest(buyerId, sellerId, subtotal, orderDiscount, orderTotal,
                                        appliedCouponValidation != null ? appliedCouponValidation.couponId() : null,
                                        appliedCouponValidation != null ? appliedCouponValidation.code() : null,
                                        paymentTransactionId, orderItems))),
                        "Order service is temporarily unavailable");

                createdOrders.add(order);
                totalPaid = totalPaid.add(orderTotal);

                // Record coupon usage per order
                if (appliedCouponValidation != null) {
                    try {
                        call("promotionService", false,
                                () -> {
                                    promotionServiceClient.applyCoupon(
                                            new PromotionApi.ApplyCouponRequest(appliedCouponValidation.couponId(), buyerId,
                                                    order.id().toString(), orderDiscount));
                                    return null;
                                },
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
                    marketplaceServiceClient.restoreInventory(
                            new MarketplaceApi.RestoreInventoryRequest(deducted.productId(), deducted.quantity()));
                } catch (BusinessException | RestClientException compensationException) {
                    log.error("Failed to restore inventory for product {}: {}",
                            deducted.productId(), compensationException.getMessage());
                }
            }
            // Compensate: refund wallet payments
            for (OrderApi.OrderResponse order : createdOrders) {
                try {
                    walletServiceClient.createRefund(
                            new WalletApi.CreateRefundRequest(buyerId, order.totalAmount(), "usd",
                                    order.id().toString(), "ORDER"));
                } catch (BusinessException | RestClientException compensationException) {
                    log.error("Failed to refund for order {}: {}", order.id(), compensationException.getMessage());
                }
            }
            // Compensate: refund loyalty points
            if (pointsUsed > 0) {
                try {
                    loyaltyServiceClient.earnPoints(
                            new LoyaltyApi.EarnPointsRequest(buyerId, "CHECKOUT_REFUND", pointsUsed,
                                    "checkout-refund-" + buyerId + "-" + System.currentTimeMillis(),
                                    "Points refund due to checkout failure"));
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
                () -> requireData(orderServiceClient.guestCheckout(request)),
                "Order service is temporarily unavailable");
    }

    public OrderApi.OrderResponse trackOrder(String orderToken) {
        return call("orderService", false,
                () -> requireData(orderServiceClient.trackOrder(orderToken)),
                "Order service is temporarily unavailable");
    }

    // ── Internal helpers ──

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

    private <T> T requireData(ApiResponse<T> response) {
        if (response == null || response.data() == null) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Empty downstream response");
        }
        return response.data();
    }

    private void clearCart(String buyerId) {
        if (guestCartStore.isGuestBuyer(buyerId)) {
            guestCartStore.clearCart(buyerId);
            return;
        }
        call("orderService", false,
                () -> {
                    orderServiceClient.clearCart(new OrderApi.ClearCartRequest(buyerId));
                    return null;
                },
                "Order service is temporarily unavailable");
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
