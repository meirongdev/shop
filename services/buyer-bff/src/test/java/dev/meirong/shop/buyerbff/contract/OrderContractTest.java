package dev.meirong.shop.buyerbff.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import dev.meirong.shop.buyerbff.config.BuyerClientProperties;
import dev.meirong.shop.buyerbff.service.BuyerAggregationService;
import dev.meirong.shop.buyerbff.service.GuestCartStore;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.resilience.ResilienceHelper;
import dev.meirong.shop.contracts.api.OrderApi;
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
 * WireMock-based contract tests verifying buyer-bff correctly serializes/deserializes
 * HTTP calls to order-service.
 */
class OrderContractTest {

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
        BuyerClientProperties properties = new BuyerClientProperties(
                baseUrl, baseUrl, baseUrl, baseUrl, baseUrl, baseUrl, baseUrl,
                Duration.ofHours(48), java.net.http.HttpClient.Version.HTTP_1_1, Duration.ofSeconds(2), Duration.ofSeconds(5));

        ResilienceHelper resilienceHelper = Mockito.mock(ResilienceHelper.class);
        Mockito.doAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get())
                .when(resilienceHelper)
                .read(Mockito.anyString(), Mockito.<Supplier<Object>>any(), Mockito.<Function<Throwable, Object>>any());
        Mockito.doAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get())
                .when(resilienceHelper)
                .write(Mockito.anyString(), Mockito.<Supplier<Object>>any(), Mockito.<Function<Throwable, Object>>any());

        aggregationService = new BuyerAggregationService(
                RestClient.builder(), null, properties, resilienceHelper,
                Mockito.mock(GuestCartStore.class), objectMapper);
    }

    @Test
    void listOrders_deserializesOrderListCorrectly() throws JsonProcessingException {
        OrderApi.OrderResponse order = new OrderApi.OrderResponse(
                UUID.randomUUID(), "buyer-001", "ORD-001", "PAID",
                new BigDecimal("99.99"), "STRIPE", Instant.now(), Instant.now(),
                null, null, null, null, List.of(), null);
        ApiResponse<List<OrderApi.OrderResponse>> apiResp = ApiResponse.success(List.of(order));

        stubFor(post(urlEqualTo(OrderApi.ORDER_LIST))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        List<OrderApi.OrderResponse> result = aggregationService.listOrders("buyer-001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).orderId()).isEqualTo("ORD-001");
        assertThat(result.get(0).status()).isEqualTo("PAID");
        assertThat(result.get(0).totalAmount()).isEqualByComparingTo("99.99");
    }

    @Test
    void getOrder_deserializesOrderDetailCorrectly() throws JsonProcessingException {
        UUID itemId = UUID.randomUUID();
        OrderApi.OrderItemResponse item = new OrderApi.OrderItemResponse(
                itemId, "prod-001", "Test Product", new BigDecimal("49.99"), 2,
                new BigDecimal("99.98"), null);
        OrderApi.OrderResponse order = new OrderApi.OrderResponse(
                UUID.randomUUID(), "buyer-001", "ORD-002", "SHIPPED",
                new BigDecimal("99.99"), "STRIPE", Instant.now(), Instant.now(),
                null, null, "TRACK123", "FedEx", List.of(item), null);
        ApiResponse<OrderApi.OrderResponse> apiResp = ApiResponse.success(order);

        stubFor(post(urlEqualTo(OrderApi.ORDER_GET))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        OrderApi.OrderResponse result = aggregationService.getOrder("buyer-001", "ORD-002");

        assertThat(result.orderId()).isEqualTo("ORD-002");
        assertThat(result.trackingNumber()).isEqualTo("TRACK123");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).productName()).isEqualTo("Test Product");
    }

    @Test
    void cancelOrder_returnsCancelledOrder() throws JsonProcessingException {
        OrderApi.OrderResponse order = new OrderApi.OrderResponse(
                UUID.randomUUID(), "buyer-001", "ORD-003", "CANCELLED",
                new BigDecimal("50.00"), "WALLET", Instant.now(), Instant.now(),
                null, null, null, null, List.of(), "User requested cancellation");
        ApiResponse<OrderApi.OrderResponse> apiResp = ApiResponse.success(order);

        stubFor(post(urlEqualTo(OrderApi.ORDER_CANCEL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        OrderApi.OrderResponse result = aggregationService.cancelOrder("buyer-001", "ORD-003");

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.cancellationReason()).isEqualTo("User requested cancellation");
    }
}
