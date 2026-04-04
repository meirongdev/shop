package dev.meirong.shop.sellerbff.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.resilience.ResilienceHelper;
import dev.meirong.shop.contracts.api.OrderApi;
import dev.meirong.shop.contracts.api.ProfileApi;
import dev.meirong.shop.contracts.api.WalletApi;
import dev.meirong.shop.sellerbff.config.SellerClientProperties;
import dev.meirong.shop.sellerbff.service.SellerAggregationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
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
        SellerClientProperties properties = new SellerClientProperties(
                baseUrl, baseUrl, baseUrl, baseUrl, baseUrl, baseUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(5));

        ResilienceHelper resilienceHelper = Mockito.mock(ResilienceHelper.class);
        Mockito.doAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get())
                .when(resilienceHelper)
                .read(Mockito.anyString(), Mockito.<Supplier<Object>>any(), Mockito.<Function<Throwable, Object>>any());
        Mockito.doAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get())
                .when(resilienceHelper)
                .write(Mockito.anyString(), Mockito.<Supplier<Object>>any(), Mockito.<Function<Throwable, Object>>any());

        aggregationService = new SellerAggregationService(
                RestClient.builder(), RestClient.builder().build(), properties, resilienceHelper, 
                new SimpleMeterRegistry());
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
