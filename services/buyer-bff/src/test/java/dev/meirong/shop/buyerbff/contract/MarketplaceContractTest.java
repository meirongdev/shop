package dev.meirong.shop.buyerbff.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.clients.loyalty.LoyaltyServiceClient;
import dev.meirong.shop.clients.marketplace.MarketplaceServiceClient;
import dev.meirong.shop.clients.order.OrderServiceClient;
import dev.meirong.shop.clients.profile.ProfileInternalServiceClient;
import dev.meirong.shop.clients.profile.ProfileServiceClient;
import dev.meirong.shop.clients.promotion.PromotionInternalServiceClient;
import dev.meirong.shop.clients.promotion.PromotionServiceClient;
import dev.meirong.shop.clients.search.SearchServiceClient;
import dev.meirong.shop.clients.wallet.WalletServiceClient;
import dev.meirong.shop.buyerbff.service.BuyerAggregationService;
import dev.meirong.shop.buyerbff.service.GuestCartStore;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.resilience.ResilienceHelper;
import dev.meirong.shop.contracts.marketplace.MarketplaceApi;
import dev.meirong.shop.contracts.promotion.PromotionApi;
import dev.meirong.shop.contracts.wallet.WalletApi;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * WireMock-based contract tests verifying buyer-bff correctly serializes/deserializes
 * HTTP calls to downstream domain services (marketplace, promotion).
 *
 * Uses a standalone WireMockServer (no Spring context) to avoid
 * dependency on Redis and other infrastructure.
 */
class MarketplaceContractTest {

    private static WireMockServer wireMock;
    private ObjectMapper objectMapper;
    private BuyerAggregationService aggregationService;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        objectMapper = new ObjectMapper().findAndRegisterModules();

        String baseUrl = "http://localhost:" + wireMock.port();

        ResilienceHelper resilienceHelper = Mockito.mock(ResilienceHelper.class);
        Mockito.doAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get())
                .when(resilienceHelper)
                .read(Mockito.anyString(), Mockito.<Supplier<Object>>any(), Mockito.<Function<Throwable, Object>>any());
        Mockito.doAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get())
                .when(resilienceHelper)
                .write(Mockito.anyString(), Mockito.<Supplier<Object>>any(), Mockito.<Function<Throwable, Object>>any());

        aggregationService = new BuyerAggregationService(
                createProxy(baseUrl, ProfileServiceClient.class),
                createProxy(baseUrl, ProfileInternalServiceClient.class),
                createProxy(baseUrl, WalletServiceClient.class),
                createProxy(baseUrl, PromotionServiceClient.class),
                createProxy(baseUrl, PromotionInternalServiceClient.class),
                createProxy(baseUrl, MarketplaceServiceClient.class),
                createProxy(baseUrl, OrderServiceClient.class),
                createProxy(baseUrl, LoyaltyServiceClient.class),
                createProxy(baseUrl, SearchServiceClient.class),
                resilienceHelper,
                Mockito.mock(GuestCartStore.class));
    }

    private <T> T createProxy(String baseUrl, Class<T> clientClass) {
        RestClient restClient = RestClient.builder().baseUrl(baseUrl).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build().createClient(clientClass);
    }

    @Test
    void listMarketplace_deserializesApiResponseCorrectly() throws JsonProcessingException {
        MarketplaceApi.ProductResponse product = new MarketplaceApi.ProductResponse(
                UUID.randomUUID(), "seller-1", "SKU-001", "Test Product", "A product",
                new BigDecimal("29.99"), 10, true, null, null, null, "ACTIVE", 0, null);
        MarketplaceApi.ProductsView view = new MarketplaceApi.ProductsView(List.of(product));
        ApiResponse<MarketplaceApi.ProductsView> apiResp = ApiResponse.success(view);

        stubFor(post(urlEqualTo(MarketplaceApi.LIST))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        List<MarketplaceApi.ProductResponse> result = aggregationService.listMarketplace();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Test Product");
        assertThat(result.get(0).price()).isEqualByComparingTo("29.99");
    }

    @Test
    void getProduct_deserializesApiResponseCorrectly() throws JsonProcessingException {
        String productId = UUID.randomUUID().toString();
        MarketplaceApi.ProductResponse product = new MarketplaceApi.ProductResponse(
                UUID.fromString(productId), "seller-1", "SKU-002", "Another Product", "Desc",
                new BigDecimal("49.99"), 5, true, "cat-1", "Electronics", null, "ACTIVE", 3,
                new BigDecimal("4.5"));
        ApiResponse<MarketplaceApi.ProductResponse> apiResp = ApiResponse.success(product);

        stubFor(post(urlEqualTo(MarketplaceApi.GET))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        MarketplaceApi.ProductResponse result = aggregationService.getProduct(productId);

        assertThat(result.id()).isEqualTo(UUID.fromString(productId));
        assertThat(result.reviewCount()).isEqualTo(3);
        assertThat(result.avgRating()).isEqualByComparingTo("4.5");
    }

    @Test
    void listPromotions_deserializesOffersCorrectly() throws JsonProcessingException {
        PromotionApi.OfferResponse offer = new PromotionApi.OfferResponse(
                UUID.randomUUID(), "SAVE10", "10% Off", "Save 10% on your order",
                new BigDecimal("10.00"), true, "SELLER", Instant.now());
        PromotionApi.OffersView offersView = new PromotionApi.OffersView(List.of(offer));
        ApiResponse<PromotionApi.OffersView> apiResp = ApiResponse.success(offersView);

        stubFor(post(urlEqualTo(PromotionApi.LIST))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        List<PromotionApi.OfferResponse> result = aggregationService.listPromotions();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("SAVE10");
    }

    @Test
    void validateCoupon_validCoupon_returnsValidResponse() throws JsonProcessingException {
        PromotionApi.CouponValidationResponse validationResp =
                new PromotionApi.CouponValidationResponse(
                        true, "coupon-id-1", "WELCOME10", "FIXED",
                        new BigDecimal("10.00"), new BigDecimal("10.00"), null);
        ApiResponse<PromotionApi.CouponValidationResponse> apiResp = ApiResponse.success(validationResp);

        stubFor(post(urlEqualTo(PromotionApi.COUPON_VALIDATE))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        PromotionApi.CouponValidationResponse result =
                aggregationService.validateCouponForCheckout("WELCOME10", new BigDecimal("100.00"));

        assertThat(result.valid()).isTrue();
        assertThat(result.discountAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void listPaymentMethods_usesGetAndDeserializesMethodsCorrectly() throws JsonProcessingException {
        WalletApi.PaymentMethodInfo wallet = new WalletApi.PaymentMethodInfo(
                "WALLET", "Wallet Balance", true, "INTERNAL");
        WalletApi.PaymentMethodInfo stripe = new WalletApi.PaymentMethodInfo(
                "STRIPE_CARD", "Credit/Debit Card", false, "STRIPE");
        ApiResponse<List<WalletApi.PaymentMethodInfo>> apiResp = ApiResponse.success(List.of(wallet, stripe));

        stubFor(get(urlEqualTo(WalletApi.PAYMENT_METHODS))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        List<WalletApi.PaymentMethodInfo> result = aggregationService.listPaymentMethods();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).method()).isEqualTo("WALLET");
        assertThat(result.get(1).provider()).isEqualTo("STRIPE");
    }
}
