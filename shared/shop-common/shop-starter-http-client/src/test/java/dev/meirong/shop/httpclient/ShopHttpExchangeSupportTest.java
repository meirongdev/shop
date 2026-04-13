package dev.meirong.shop.httpclient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.httpclient.error.SharedDownstreamErrorHandler;
import dev.meirong.shop.httpclient.interceptor.TracingHeaderInterceptor;
import dev.meirong.shop.httpclient.interceptor.TracingHeaderInterceptor.BaggageMapping;
import dev.meirong.shop.httpclient.support.ShopHttpExchangeSupport;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

class ShopHttpExchangeSupportTest {

    private static WireMockServer wireMock;
    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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
    }

    @Test
    void createsProxy_andCallsDownstream() throws IOException {
        String baseUrl = "http://localhost:" + wireMock.port();
        ApiResponse<TestDto> apiResp = ApiResponse.success(new TestDto("hello", 42));

        stubFor(get(urlEqualTo("/api/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        TestClient client = createClient(baseUrl);
        TestDto result = client.getTest();

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("hello");
        assertThat(result.value()).isEqualTo(42);
    }

    @Test
    void propagatesTracingHeaders() throws IOException {
        String baseUrl = "http://localhost:" + wireMock.port();
        ApiResponse<TestDto> apiResp = ApiResponse.success(new TestDto("ok", 1));

        stubFor(get(urlEqualTo("/api/test"))
                .withHeader("X-Buyer-Id", equalTo("buyer-001"))
                .withHeader("X-Username", equalTo("alice"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(apiResp))));

        // Set baggage values for this test
        BaggageMapping.buyerIdField().updateValue("buyer-001");
        BaggageMapping.usernameField().updateValue("alice");

        TestClient client = createClient(baseUrl);
        TestDto result = client.getTest();

        assertThat(result).isNotNull();
    }

    @Test
    void downstream4xxError_wrapsAsBusinessException() throws IOException {
        String baseUrl = "http://localhost:" + wireMock.port();
        ApiResponse<Object> errorResp = ApiResponse.error("SC_VALIDATION_ERROR", "Invalid input");

        stubFor(get(urlEqualTo("/api/test"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(errorResp))));

        TestClient client = createClient(baseUrl);

        assertThatThrownBy(client::getTest)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid input");
    }

    @Test
    void downstream5xxError_wrapsAsBusinessException() throws IOException {
        String baseUrl = "http://localhost:" + wireMock.port();
        ApiResponse<Object> errorResp = ApiResponse.error("SC_INTERNAL_ERROR", "Internal error");

        stubFor(get(urlEqualTo("/api/test"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(errorResp))));

        TestClient client = createClient(baseUrl);

        assertThatThrownBy(client::getTest)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Internal error");
    }

    private TestClient createClient(String baseUrl) {
        TracingHeaderInterceptor interceptor = new TracingHeaderInterceptor(
                List.of(BaggageMapping.buyerIdField(), BaggageMapping.usernameField()));
        SharedDownstreamErrorHandler errorHandler = new SharedDownstreamErrorHandler(objectMapper);

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(interceptor)
                .defaultStatusHandler(
                        org.springframework.http.HttpStatusCode::isError,
                        errorHandler::handleError)
                .build();

        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();

        return factory.createClient(TestClient.class);
    }

    @HttpExchange
    interface TestClient {
        @GetExchange("/api/test")
        TestDto getTest();
    }

    record TestDto(String name, int value) {
    }
}
