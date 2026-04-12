package dev.meirong.shop.sellerbff.service;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.metrics.MetricsHelper;
import dev.meirong.shop.common.resilience.ResilienceHelper;
import dev.meirong.shop.contracts.marketplace.MarketplaceApi;
import dev.meirong.shop.contracts.order.OrderApi;
import dev.meirong.shop.contracts.profile.ProfileApi;
import dev.meirong.shop.contracts.promotion.PromotionApi;
import dev.meirong.shop.contracts.search.SearchApi;
import dev.meirong.shop.contracts.seller.SellerApi;
import dev.meirong.shop.contracts.wallet.WalletApi;
import dev.meirong.shop.sellerbff.client.MarketplaceServiceClient;
import dev.meirong.shop.sellerbff.client.OrderServiceClient;
import dev.meirong.shop.sellerbff.client.ProfileServiceClient;
import dev.meirong.shop.sellerbff.client.PromotionServiceClient;
import dev.meirong.shop.sellerbff.client.SearchServiceClient;
import dev.meirong.shop.sellerbff.client.WalletServiceClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SellerAggregationService {

    private static final Logger log = LoggerFactory.getLogger(SellerAggregationService.class);

    private final ProfileServiceClient profileClient;
    private final MarketplaceServiceClient marketplaceClient;
    private final OrderServiceClient orderClient;
    private final WalletServiceClient walletClient;
    private final PromotionServiceClient promotionClient;
    private final SearchServiceClient searchClient;
    private final ResilienceHelper resilienceHelper;
    private final MetricsHelper metrics;

    public SellerAggregationService(ProfileServiceClient profileClient,
                                    MarketplaceServiceClient marketplaceClient,
                                    OrderServiceClient orderClient,
                                    WalletServiceClient walletClient,
                                    PromotionServiceClient promotionClient,
                                    SearchServiceClient searchClient,
                                    ResilienceHelper resilienceHelper,
                                    MeterRegistry meterRegistry) {
        this.profileClient = profileClient;
        this.marketplaceClient = marketplaceClient;
        this.orderClient = orderClient;
        this.walletClient = walletClient;
        this.promotionClient = promotionClient;
        this.searchClient = searchClient;
        this.resilienceHelper = resilienceHelper;
        this.metrics = new MetricsHelper("seller-bff", meterRegistry);
    }

    public SellerApi.DashboardResponse loadDashboard(String sellerId) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var productsFuture = executor.submit(() -> listProductsForSeller(sellerId));
                var offersFuture = executor.submit(this::listOffersForSeller);
                List<MarketplaceApi.ProductResponse> products = productsFuture.get();
                List<PromotionApi.OfferResponse> offers = offersFuture.get();
                long activePromotionCount = offers.stream().filter(offer -> sellerId.equals(offer.source())).count();
                return new SellerApi.DashboardResponse(products.size(), activePromotionCount, products, offers);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Seller dashboard interrupted", exception);
        } catch (ExecutionException exception) {
            result = "failure";
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Seller dashboard aggregation failed", exception);
        } finally {
            metrics.increment("shop_seller_dashboard_operation_total", "operation", "load", "result", result);
            sample.stop(metrics.timer("shop_seller_dashboard_operation_duration_seconds", "operation", "load", "result", result));
        }
    }

    public MarketplaceApi.ProductResponse createProduct(MarketplaceApi.UpsertProductRequest request) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            return call("marketplaceService", false,
                    () -> requireData(marketplaceClient.createProduct(request)),
                    "Marketplace service is temporarily unavailable");
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            metrics.increment("shop_seller_product_operation_total", "operation", "create", "result", result);
            sample.stop(metrics.timer("shop_seller_product_operation_duration_seconds", "operation", "create", "result", result));
        }
    }

    public MarketplaceApi.ProductResponse updateProduct(MarketplaceApi.UpsertProductRequest request) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            return call("marketplaceService", false,
                    () -> requireData(marketplaceClient.updateProduct(request)),
                    "Marketplace service is temporarily unavailable");
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            metrics.increment("shop_seller_product_operation_total", "operation", "update", "result", result);
            sample.stop(metrics.timer("shop_seller_product_operation_duration_seconds", "operation", "update", "result", result));
        }
    }

    public PromotionApi.OfferResponse createPromotion(PromotionApi.CreateOfferRequest request) {
        return call("promotionService", false,
                () -> requireData(promotionClient.createOffer(request)),
                "Promotion service is temporarily unavailable");
    }

    public List<OrderApi.OrderResponse> listOrders(String sellerId) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            return call("orderService", false,
                    () -> requireData(orderClient.listOrders(new OrderApi.ListOrdersRequest(sellerId, "seller"))),
                    "Order service is temporarily unavailable");
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            metrics.increment("shop_seller_order_operation_total", "operation", "list", "result", result);
            sample.stop(metrics.timer("shop_seller_order_operation_duration_seconds", "operation", "list", "result", result));
        }
    }

    public OrderApi.OrderResponse getOrder(String orderId) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            return call("orderService", false,
                    () -> requireData(orderClient.getOrder(new OrderApi.GetOrderRequest(orderId))),
                    "Order service is temporarily unavailable");
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            metrics.increment("shop_seller_order_operation_total", "operation", "get", "result", result);
            sample.stop(metrics.timer("shop_seller_order_operation_duration_seconds", "operation", "get", "result", result));
        }
    }

    public OrderApi.OrderResponse shipOrder(String orderId) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            return call("orderService", false,
                    () -> requireData(orderClient.shipOrder(new OrderApi.ShipOrderRequest(orderId, null, "PENDING"))),
                    "Order service is temporarily unavailable");
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            metrics.increment("shop_seller_order_operation_total", "operation", "ship", "result", result);
            sample.stop(metrics.timer("shop_seller_order_operation_duration_seconds", "operation", "ship", "result", result));
        }
    }

    public OrderApi.OrderResponse deliverOrder(String orderId) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            return call("orderService", false,
                    () -> requireData(orderClient.deliverOrder(new OrderApi.ConfirmOrderRequest(orderId))),
                    "Order service is temporarily unavailable");
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            metrics.increment("shop_seller_order_operation_total", "operation", "deliver", "result", result);
            sample.stop(metrics.timer("shop_seller_order_operation_duration_seconds", "operation", "deliver", "result", result));
        }
    }

    public OrderApi.OrderResponse cancelOrder(String orderId, String reason) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            return call("orderService", false,
                    () -> requireData(orderClient.cancelOrder(new OrderApi.CancelOrderRequest(orderId, reason))),
                    "Order service is temporarily unavailable");
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            metrics.increment("shop_seller_order_operation_total", "operation", "cancel", "result", result);
            sample.stop(metrics.timer("shop_seller_order_operation_duration_seconds", "operation", "cancel", "result", result));
        }
    }

    public WalletApi.WalletAccountResponse getWallet(String sellerId) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            return call("walletService", false,
                    () -> requireData(walletClient.getWallet(new WalletApi.GetWalletRequest(sellerId))),
                    "Wallet service is temporarily unavailable");
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            metrics.increment("shop_seller_wallet_operation_total", "operation", "get", "result", result);
            sample.stop(metrics.timer("shop_seller_wallet_operation_duration_seconds", "operation", "get", "result", result));
        }
    }

    public WalletApi.TransactionResponse withdrawWallet(WalletApi.WithdrawRequest request) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            return call("walletService", false,
                    () -> requireData(walletClient.withdraw(request)),
                    "Wallet service is temporarily unavailable");
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            metrics.increment("shop_seller_wallet_operation_total", "operation", "withdraw", "result", result);
            sample.stop(metrics.timer("shop_seller_wallet_operation_duration_seconds", "operation", "withdraw", "result", result));
        }
    }

    public ProfileApi.ProfileResponse getProfile(String sellerId) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            return call("profileService", true,
                    () -> requireData(profileClient.getSellerProfile(new ProfileApi.GetProfileRequest(sellerId))),
                    "Profile service is temporarily unavailable");
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            metrics.increment("shop_seller_profile_operation_total", "operation", "get", "result", result);
            sample.stop(metrics.timer("shop_seller_profile_operation_duration_seconds", "operation", "get", "result", result));
        }
    }

    public ProfileApi.ProfileResponse updateProfile(ProfileApi.UpdateProfileRequest request) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            return call("profileService", false,
                    () -> requireData(profileClient.updateSellerProfile(request)),
                    "Profile service is temporarily unavailable");
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            metrics.increment("shop_seller_profile_operation_total", "operation", "update", "result", result);
            sample.stop(metrics.timer("shop_seller_profile_operation_duration_seconds", "operation", "update", "result", result));
        }
    }

    public ProfileApi.SellerStorefrontResponse getShop(String sellerId) {
        return call("profileService", true,
                () -> requireData(profileClient.getSellerStorefront(new ProfileApi.GetProfileRequest(sellerId))),
                "Profile service is temporarily unavailable");
    }

    public ProfileApi.SellerStorefrontResponse updateShop(ProfileApi.UpdateShopRequest request) {
        return call("profileService", false,
                () -> requireData(profileClient.updateSellerShop(request)),
                "Profile service is temporarily unavailable");
    }

    public PromotionApi.CouponResponse createCoupon(PromotionApi.CreateCouponRequest request) {
        return call("promotionService", false,
                () -> requireData(promotionClient.createCoupon(request)),
                "Promotion service is temporarily unavailable");
    }

    public List<PromotionApi.CouponResponse> listCoupons(String sellerId) {
        return call("promotionService", true,
                () -> requireData(promotionClient.listCoupons(new PromotionApi.ListCouponsRequest(sellerId))),
                "Promotion service is temporarily unavailable");
    }

    public SearchApi.SearchProductsResponse searchProducts(MarketplaceApi.SearchProductsRequest request) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            return call("searchService", true,
                    () -> requireData(searchClient.searchProducts(
                            request.query(), request.categoryId(), request.page(), request.size())),
                    throwable -> searchProductsFallback(request, throwable));
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            metrics.increment("shop_seller_product_operation_total", "operation", "search", "result", result);
            sample.stop(metrics.timer("shop_seller_product_operation_duration_seconds", "operation", "search", "result", result));
        }
    }

    public SearchApi.SearchProductsResponse searchProductsFallback(MarketplaceApi.SearchProductsRequest request, Throwable throwable) {
        log.warn("search-service unavailable, fallback to marketplace search: {}", throwable.getMessage());
        MarketplaceApi.ProductsPageView fallback = call("marketplaceService", true,
                () -> requireData(marketplaceClient.searchProducts(request)),
                "Marketplace service is temporarily unavailable");
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

    private List<MarketplaceApi.ProductResponse> listProductsForSeller(String sellerId) {
        MarketplaceApi.ProductsView response = call("marketplaceService", true,
                () -> requireData(marketplaceClient.listProducts(new MarketplaceApi.ListProductsRequest(false))),
                throwable -> {
                    log.warn("marketplace-service unavailable for seller dashboard products: {}", throwable.getMessage());
                    return new MarketplaceApi.ProductsView(List.of());
                });
        return response.products().stream().filter(product -> sellerId.equals(product.sellerId())).toList();
    }

    private List<PromotionApi.OfferResponse> listOffersForSeller() {
        PromotionApi.OffersView response = call("promotionService", true,
                () -> requireData(promotionClient.listOffers(new PromotionApi.ListOffersRequest(null))),
                throwable -> {
                    log.warn("promotion-service unavailable for seller dashboard offers: {}", throwable.getMessage());
                    return new PromotionApi.OffersView(List.of());
                });
        return response.offers();
    }

    private <T> T requireData(ApiResponse<T> response) {
        if (response == null || response.data() == null) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Empty downstream response");
        }
        return response.data();
    }

    private <T> T call(String instanceName, boolean retryEnabled, Supplier<T> supplier, String unavailableMessage) {
        return call(instanceName, retryEnabled, supplier, throwable -> failDownstream(unavailableMessage, throwable));
    }

    private <T> T call(String instanceName,
                       boolean retryEnabled,
                       Supplier<T> supplier,
                       Function<Throwable, T> fallback) {
        return retryEnabled
                ? resilienceHelper.read(instanceName, supplier, fallback)
                : resilienceHelper.write(instanceName, supplier, fallback);
    }

    private <T> T failDownstream(String message, Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            throw businessException;
        }
        throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, message, throwable);
    }
}
