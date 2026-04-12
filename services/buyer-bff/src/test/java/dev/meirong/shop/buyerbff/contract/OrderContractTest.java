package dev.meirong.shop.buyerbff.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import dev.meirong.shop.buyerbff.client.LoyaltyServiceClient;
import dev.meirong.shop.buyerbff.client.MarketplaceServiceClient;
import dev.meirong.shop.buyerbff.client.OrderServiceClient;
import dev.meirong.shop.buyerbff.client.ProfileInternalServiceClient;
import dev.meirong.shop.buyerbff.client.ProfileServiceClient;
import dev.meirong.shop.buyerbff.client.PromotionInternalServiceClient;
import dev.meirong.shop.buyerbff.client.PromotionServiceClient;
import dev.meirong.shop.buyerbff.client.SearchServiceClient;
import dev.meirong.shop.buyerbff.client.WalletServiceClient;
import dev.meirong.shop.buyerbff.service.BuyerAggregationService;
import dev.meirong.shop.buyerbff.service.GuestCartStore;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.resilience.ResilienceHelper;
import dev.meirong.shop.contracts.order.OrderApi;
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
    void listOrders_deserializesOrderListCorrectly() throws JsonProcessingException {
        OrderApi.OrderResponse order = new OrderApi.OrderResponse(
                UUID.randomUUID(), "ORD-001", "BUYER", null, "buyer-001", "seller-001", "PAID",
                new BigDecimal("89.99"), new BigDecimal("10.00"), new BigDecimal("99.99"),
                null, null, "txn-123", List.of(),
                Instant.now(), null, null, null, null, Instant.now(), Instant.now());
        ApiResponse<List<OrderApi.OrderResponse>> apiResp = ApiResponse.success(List.of(order));

        stubFor(post(urlEqualTo(OrderApi.ORDER_LIST))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        List<OrderApi.OrderResponse> result = aggregationService.listOrders("buyer-001", "BUYER");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).orderNo()).isEqualTo("ORD-001");
        assertThat(result.get(0).status()).isEqualTo("PAID");
        assertThat(result.get(0).totalAmount()).isEqualByComparingTo("99.99");
    }

    @Test
    void getOrder_deserializesOrderDetailCorrectly() throws JsonProcessingException {
        UUID itemId = UUID.randomUUID();
        OrderApi.OrderItemResponse item = new OrderApi.OrderItemResponse(
                itemId, "ORD-002", "prod-001", "Test Product", new BigDecimal("49.99"), 2,
                new BigDecimal("99.98"));
        OrderApi.OrderResponse order = new OrderApi.OrderResponse(
                UUID.randomUUID(), "ORD-002", "BUYER", null, "buyer-001", "seller-001", "SHIPPED",
                new BigDecimal("89.99"), new BigDecimal("10.00"), new BigDecimal("99.99"),
                null, null, "txn-456", List.of(item),
                Instant.now(), Instant.now(), null, null, null, Instant.now(), Instant.now());
        ApiResponse<OrderApi.OrderResponse> apiResp = ApiResponse.success(order);

        stubFor(post(urlEqualTo(OrderApi.ORDER_GET))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        OrderApi.OrderResponse result = aggregationService.getOrder("ORD-002");

        assertThat(result.orderNo()).isEqualTo("ORD-002");
        assertThat(result.status()).isEqualTo("SHIPPED");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).productName()).isEqualTo("Test Product");
    }

    @Test
    void cancelOrder_returnsCancelledOrder() throws JsonProcessingException {
        OrderApi.OrderResponse existingOrder = new OrderApi.OrderResponse(
                UUID.randomUUID(), "ORD-003", "BUYER", null, "buyer-001", "seller-001", "PAID",
                new BigDecimal("50.00"), new BigDecimal("0.00"), new BigDecimal("50.00"),
                null, null, "txn-789", List.of(),
                Instant.now(), null, null, null, Instant.now(), Instant.now(), Instant.now());
        ApiResponse<OrderApi.OrderResponse> getOrderResp = ApiResponse.success(existingOrder);

        stubFor(post(urlEqualTo(OrderApi.ORDER_GET))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(getOrderResp))));

        OrderApi.OrderResponse cancelledOrder = new OrderApi.OrderResponse(
                existingOrder.id(), "ORD-003", "BUYER", null, "buyer-001", "seller-001", "CANCELLED",
                new BigDecimal("50.00"), new BigDecimal("0.00"), new BigDecimal("50.00"),
                null, null, "txn-789", List.of(),
                Instant.now(), null, null, null, Instant.now(), Instant.now(), Instant.now());
        ApiResponse<OrderApi.OrderResponse> cancelResp = ApiResponse.success(cancelledOrder);

        stubFor(post(urlEqualTo(OrderApi.ORDER_CANCEL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(cancelResp))));

        OrderApi.OrderResponse result = aggregationService.cancelOrder("ORD-003", "buyer-001");

        assertThat(result.status()).isEqualTo("CANCELLED");
    }
}
