package dev.meirong.shop.sellerbff.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.resilience.ResilienceHelper;
import dev.meirong.shop.contracts.order.OrderApi;
import dev.meirong.shop.contracts.profile.ProfileApi;
import dev.meirong.shop.contracts.wallet.WalletApi;
import dev.meirong.shop.clients.marketplace.MarketplaceServiceClient;
import dev.meirong.shop.clients.order.OrderServiceClient;
import dev.meirong.shop.clients.profile.ProfileServiceClient;
import dev.meirong.shop.clients.promotion.PromotionServiceClient;
import dev.meirong.shop.clients.search.SearchServiceClient;
import dev.meirong.shop.clients.wallet.WalletServiceClient;
import dev.meirong.shop.sellerbff.service.SellerAggregationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.http.HttpClient;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * WireMock-based contract tests verifying seller-bff correctly serializes/deserializes
 * HTTP calls to downstream domain services (order, profile, wallet).
 */
class SellerContractTest {

    private static WireMockServer wireMock;
    private ObjectMapper objectMapper;
    private SellerAggregationService aggregationService;

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
        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);

        ResilienceHelper resilienceHelper = Mockito.mock(ResilienceHelper.class);
        Mockito.doAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get())
                .when(resilienceHelper)
                .read(Mockito.anyString(), Mockito.<Supplier<Object>>any(), Mockito.<Function<Throwable, Object>>any());
        Mockito.doAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get())
                .when(resilienceHelper)
                .write(Mockito.anyString(), Mockito.<Supplier<Object>>any(), Mockito.<Function<Throwable, Object>>any());

        aggregationService = new SellerAggregationService(
                createClient(baseUrl, factory, ProfileServiceClient.class),
                createClient(baseUrl, factory, MarketplaceServiceClient.class),
                createClient(baseUrl, factory, OrderServiceClient.class),
                createClient(baseUrl, factory, WalletServiceClient.class),
                createClient(baseUrl, factory, PromotionServiceClient.class),
                createClient(baseUrl, factory, SearchServiceClient.class),
                resilienceHelper,
                new SimpleMeterRegistry());
    }

    private <T> T createClient(String baseUrl, JdkClientHttpRequestFactory factory, Class<T> clientType) {
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .build();
        HttpServiceProxyFactory proxyFactory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return proxyFactory.createClient(clientType);
    }
    @Test
    void getOrder_deserializesOrderCorrectly() throws JsonProcessingException {
        ApiResponse<OrderApi.OrderResponse> apiResp = ApiResponse.success(new OrderApi.OrderResponse(
                UUID.randomUUID(), "ORD-001", "BUYER", null, "buyer-001", "seller-001", "PAID",
                new BigDecimal("89.99"), new BigDecimal("10.00"), new BigDecimal("99.99"),
                null, null, "txn-123", List.of(),
                Instant.now(), null, null, null, null, Instant.now(), Instant.now()));

        stubFor(post(urlEqualTo(OrderApi.ORDER_GET))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        OrderApi.OrderResponse result = aggregationService.getOrder("ORD-001");

        assertThat(result.orderNo()).isEqualTo("ORD-001");
        assertThat(result.status()).isEqualTo("PAID");
        assertThat(result.totalAmount()).isEqualByComparingTo("99.99");
    }

    @Test
    void getWallet_deserializesWalletCorrectly() throws JsonProcessingException {
        ApiResponse<WalletApi.WalletAccountResponse> apiResp = ApiResponse.success(
                new WalletApi.WalletAccountResponse("seller-001", new BigDecimal("5000.00"), Instant.now(), List.of()));

        stubFor(post(urlEqualTo(WalletApi.GET))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        WalletApi.WalletAccountResponse result = aggregationService.getWallet("seller-001");

        assertThat(result.balance()).isEqualByComparingTo("5000.00");
        assertThat(result.buyerId()).isEqualTo("seller-001");
    }

    @Test
    void getProfile_deserializesProfileCorrectly() throws JsonProcessingException {
        ApiResponse<ProfileApi.ProfileResponse> apiResp = ApiResponse.success(
                new ProfileApi.ProfileResponse("seller-001", "Test Seller", "Test Display", "seller@example.com", "BRONZE", Instant.now(), Instant.now()));

        stubFor(post(urlEqualTo(ProfileApi.SELLER_GET))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        ProfileApi.ProfileResponse result = aggregationService.getProfile("seller-001");

        assertThat(result.buyerId()).isEqualTo("seller-001");
        assertThat(result.username()).isEqualTo("Test Seller");
    }
}
