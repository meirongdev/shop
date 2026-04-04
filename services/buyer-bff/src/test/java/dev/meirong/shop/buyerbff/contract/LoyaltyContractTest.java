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
import dev.meirong.shop.contracts.api.LoyaltyApi;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
