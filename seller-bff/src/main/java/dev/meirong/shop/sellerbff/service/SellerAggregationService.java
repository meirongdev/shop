package dev.meirong.shop.sellerbff.service;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.resilience.ResilienceHelper;
import dev.meirong.shop.contracts.api.MarketplaceApi;
import dev.meirong.shop.contracts.api.OrderApi;
import dev.meirong.shop.contracts.api.ProfileApi;
import dev.meirong.shop.contracts.api.PromotionApi;
import dev.meirong.shop.contracts.api.SearchApi;
import dev.meirong.shop.contracts.api.SellerApi;
import dev.meirong.shop.contracts.api.WalletApi;
import dev.meirong.shop.sellerbff.config.SellerClientProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class SellerAggregationService {

    private static final Logger log = LoggerFactory.getLogger(SellerAggregationService.class);

    private final RestClient restClient;
    private final RestClient searchRestClient;
    private final SellerClientProperties properties;
    private final ResilienceHelper resilienceHelper;

    public SellerAggregationService(RestClient.Builder builder,
                                    @Qualifier("searchRestClient") RestClient searchRestClient,
                                    SellerClientProperties properties,
                                    ResilienceHelper resilienceHelper) {
        this.restClient = builder.build();
        this.searchRestClient = searchRestClient;
        this.properties = properties;
        this.resilienceHelper = resilienceHelper;
    }

    public SellerApi.DashboardResponse loadDashboard(String sellerId) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var productsFuture = executor.submit(() -> listProductsForSeller(sellerId));
            var offersFuture = executor.submit(this::listOffersForSeller);
            List<MarketplaceApi.ProductResponse> products = productsFuture.get();
            List<PromotionApi.OfferResponse> offers = offersFuture.get();
            long activePromotionCount = offers.stream().filter(offer -> sellerId.equals(offer.source())).count();
            return new SellerApi.DashboardResponse(products.size(), activePromotionCount, products, offers);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Seller dashboard interrupted", exception);
        } catch (ExecutionException exception) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Seller dashboard aggregation failed", exception);
        }
    }

    public MarketplaceApi.ProductResponse createProduct(MarketplaceApi.UpsertProductRequest request) {
        return call("marketplaceService", false,
                () -> post(properties.marketplaceServiceUrl() + MarketplaceApi.CREATE, request,
                        new ParameterizedTypeReference<ApiResponse<MarketplaceApi.ProductResponse>>() {}),
                "Marketplace service is temporarily unavailable");
    }

    public MarketplaceApi.ProductResponse updateProduct(MarketplaceApi.UpsertProductRequest request) {
        return call("marketplaceService", false,
                () -> post(properties.marketplaceServiceUrl() + MarketplaceApi.UPDATE, request,
                        new ParameterizedTypeReference<ApiResponse<MarketplaceApi.ProductResponse>>() {}),
                "Marketplace service is temporarily unavailable");
    }

    public PromotionApi.OfferResponse createPromotion(PromotionApi.CreateOfferRequest request) {
        return call("promotionService", false,
                () -> post(properties.promotionServiceUrl() + PromotionApi.CREATE, request,
                        new ParameterizedTypeReference<ApiResponse<PromotionApi.OfferResponse>>() {}),
                "Promotion service is temporarily unavailable");
    }

    public List<OrderApi.OrderResponse> listOrders(String sellerId) {
        return call("orderService", false,
                () -> post(properties.orderServiceUrl() + OrderApi.ORDER_LIST,
                        new OrderApi.ListOrdersRequest(sellerId, "seller"),
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

    public OrderApi.OrderResponse shipOrder(String orderId) {
        return call("orderService", false,
                () -> post(properties.orderServiceUrl() + OrderApi.ORDER_SHIP,
                        new OrderApi.ShipOrderRequest(orderId, null, "PENDING"),
                        new ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {}),
                "Order service is temporarily unavailable");
    }

    public OrderApi.OrderResponse deliverOrder(String orderId) {
        return call("orderService", false,
                () -> post(properties.orderServiceUrl() + OrderApi.ORDER_DELIVER,
                        new OrderApi.ConfirmOrderRequest(orderId),
                        new ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {}),
                "Order service is temporarily unavailable");
    }

    public OrderApi.OrderResponse cancelOrder(String orderId, String reason) {
        return call("orderService", false,
                () -> post(properties.orderServiceUrl() + OrderApi.ORDER_CANCEL,
                        new OrderApi.CancelOrderRequest(orderId, reason),
                        new ParameterizedTypeReference<ApiResponse<OrderApi.OrderResponse>>() {}),
                "Order service is temporarily unavailable");
    }

    public WalletApi.WalletAccountResponse getWallet(String sellerId) {
        return call("walletService", false,
                () -> post(properties.walletServiceUrl() + WalletApi.GET,
                        new WalletApi.GetWalletRequest(sellerId),
                        new ParameterizedTypeReference<ApiResponse<WalletApi.WalletAccountResponse>>() {}),
                "Wallet service is temporarily unavailable");
    }

    public WalletApi.TransactionResponse withdrawWallet(WalletApi.WithdrawRequest request) {
        return call("walletService", false,
                () -> post(properties.walletServiceUrl() + WalletApi.WITHDRAW,
                        request,
                        new ParameterizedTypeReference<ApiResponse<WalletApi.TransactionResponse>>() {}),
                "Wallet service is temporarily unavailable");
    }

    public ProfileApi.ProfileResponse getProfile(String sellerId) {
        return call("profileService", true,
                () -> post(properties.profileServiceUrl() + ProfileApi.SELLER_GET,
                        new ProfileApi.GetProfileRequest(sellerId),
                        new ParameterizedTypeReference<ApiResponse<ProfileApi.ProfileResponse>>() {}),
                "Profile service is temporarily unavailable");
    }

    public ProfileApi.ProfileResponse updateProfile(ProfileApi.UpdateProfileRequest request) {
        return call("profileService", false,
                () -> post(properties.profileServiceUrl() + ProfileApi.SELLER_UPDATE, request,
                        new ParameterizedTypeReference<ApiResponse<ProfileApi.ProfileResponse>>() {}),
                "Profile service is temporarily unavailable");
    }

    public ProfileApi.SellerStorefrontResponse getShop(String sellerId) {
        return call("profileService", true,
                () -> post(properties.profileServiceUrl() + ProfileApi.SELLER_STOREFRONT,
                        new ProfileApi.GetProfileRequest(sellerId),
                        new ParameterizedTypeReference<ApiResponse<ProfileApi.SellerStorefrontResponse>>() {}),
                "Profile service is temporarily unavailable");
    }

    public ProfileApi.SellerStorefrontResponse updateShop(ProfileApi.UpdateShopRequest request) {
        return call("profileService", false,
                () -> post(properties.profileServiceUrl() + ProfileApi.SELLER_SHOP_UPDATE, request,
                        new ParameterizedTypeReference<ApiResponse<ProfileApi.SellerStorefrontResponse>>() {}),
                "Profile service is temporarily unavailable");
    }

    public PromotionApi.CouponResponse createCoupon(PromotionApi.CreateCouponRequest request) {
        return call("promotionService", false,
                () -> post(properties.promotionServiceUrl() + PromotionApi.COUPON_CREATE, request,
                        new ParameterizedTypeReference<ApiResponse<PromotionApi.CouponResponse>>() {}),
                "Promotion service is temporarily unavailable");
    }

    public List<PromotionApi.CouponResponse> listCoupons(String sellerId) {
        return call("promotionService", true,
                () -> post(properties.promotionServiceUrl() + PromotionApi.COUPON_LIST,
                        new PromotionApi.ListCouponsRequest(sellerId),
                        new ParameterizedTypeReference<ApiResponse<List<PromotionApi.CouponResponse>>>() {}),
                "Promotion service is temporarily unavailable");
    }

    public SearchApi.SearchProductsResponse searchProducts(MarketplaceApi.SearchProductsRequest request) {
        return call("searchService", true,
                () -> searchProductsFromSearchService(request),
                throwable -> searchProductsFallback(request, throwable));
    }

    public SearchApi.SearchProductsResponse searchProductsFallback(MarketplaceApi.SearchProductsRequest request, Throwable throwable) {
        log.warn("search-service unavailable, fallback to marketplace search: {}", throwable.getMessage());
        MarketplaceApi.ProductsPageView fallback = call("marketplaceService", true,
                () -> post(
                        properties.marketplaceServiceUrl() + MarketplaceApi.SEARCH,
                        request,
                        new ParameterizedTypeReference<ApiResponse<MarketplaceApi.ProductsPageView>>() {}),
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
                () -> post(
                        properties.marketplaceServiceUrl() + MarketplaceApi.LIST,
                        new MarketplaceApi.ListProductsRequest(false),
                        new ParameterizedTypeReference<ApiResponse<MarketplaceApi.ProductsView>>() {}),
                throwable -> {
                    log.warn("marketplace-service unavailable for seller dashboard products: {}", throwable.getMessage());
                    return new MarketplaceApi.ProductsView(List.of());
                });
        return response.products().stream().filter(product -> sellerId.equals(product.sellerId())).toList();
    }

    private List<PromotionApi.OfferResponse> listOffersForSeller() {
        PromotionApi.OffersView response = call("promotionService", true,
                () -> post(
                        properties.promotionServiceUrl() + PromotionApi.LIST,
                        new PromotionApi.ListOffersRequest(null),
                        new ParameterizedTypeReference<ApiResponse<PromotionApi.OffersView>>() {}),
                throwable -> {
                    log.warn("promotion-service unavailable for seller dashboard offers: {}", throwable.getMessage());
                    return new PromotionApi.OffersView(List.of());
                });
        return response.offers();
    }

    private SearchApi.SearchProductsResponse searchProductsFromSearchService(MarketplaceApi.SearchProductsRequest request) {
        String uri = UriComponentsBuilder.fromPath(SearchApi.SEARCH_PRODUCTS)
                .queryParamIfPresent("q", Optional.ofNullable(request.query()))
                .queryParamIfPresent("categoryId", Optional.ofNullable(request.categoryId()))
                .queryParam("page", request.page())
                .queryParam("hitsPerPage", request.size())
                .build()
                .toUriString();
        ApiResponse<SearchApi.SearchProductsResponse> response = searchRestClient.get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<SearchApi.SearchProductsResponse>>() {});
        if (response == null || response.data() == null) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Empty downstream response from " + uri);
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

    private <T> T post(String url, Object request, ParameterizedTypeReference<ApiResponse<T>> typeReference) {
        ApiResponse<T> response = restClient.post()
                .uri(url)
                .header("X-Internal-Token", properties.internalToken())
                .body(request)
                .retrieve()
                .body(typeReference);
        if (response == null || response.data() == null) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Empty downstream response from " + url);
        }
        return response.data();
    }
}
