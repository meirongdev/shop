package dev.meirong.shop.buyerbff.contract;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
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
import dev.meirong.shop.contracts.loyalty.LoyaltyApi;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * HTTP calls to loyalty-service.
 */
class LoyaltyContractTest {

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
    void getLoyaltyAccount_deserializesAccountResponseCorrectly() throws JsonProcessingException {
        LoyaltyApi.AccountResponse account = new LoyaltyApi.AccountResponse(
                "buyer-001", 2000L, 500L, 1500L, "BRONZE", 100L);
        ApiResponse<LoyaltyApi.AccountResponse> apiResp = ApiResponse.success(account);

        stubFor(get(urlEqualTo(LoyaltyApi.INTERNAL_BALANCE + "/buyer-001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        LoyaltyApi.AccountResponse result = aggregationService.getLoyaltyAccount("buyer-001");

        assertThat(result.buyerId()).isEqualTo("buyer-001");
        assertThat(result.balance()).isEqualTo(1500L);
        assertThat(result.tier()).isEqualTo("BRONZE");
    }

    @Test
    void loyaltyCheckin_returnsCheckInResult() throws JsonProcessingException {
        LoyaltyApi.CheckinResponse checkIn = new LoyaltyApi.CheckinResponse(
                "checkin-001", LocalDate.now(), 3, 50L, false, 0L);
        ApiResponse<LoyaltyApi.CheckinResponse> apiResp = ApiResponse.success(checkIn);

        stubFor(post(urlEqualTo(LoyaltyApi.CHECKIN))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        LoyaltyApi.CheckinResponse result = aggregationService.loyaltyCheckin("buyer-001");

        assertThat(result.streakDay()).isEqualTo(3);
        assertThat(result.pointsEarned()).isEqualTo(50L);
        assertThat(result.isMakeup()).isFalse();
    }
}
