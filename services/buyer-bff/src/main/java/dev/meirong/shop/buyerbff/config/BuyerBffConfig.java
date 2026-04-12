package dev.meirong.shop.buyerbff.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.buyerbff.client.LoyaltyServiceClient;
import dev.meirong.shop.buyerbff.client.MarketplaceServiceClient;
import dev.meirong.shop.buyerbff.client.OrderServiceClient;
import dev.meirong.shop.buyerbff.client.ProfileInternalServiceClient;
import dev.meirong.shop.buyerbff.client.ProfileServiceClient;
import dev.meirong.shop.buyerbff.client.PromotionInternalServiceClient;
import dev.meirong.shop.buyerbff.client.PromotionServiceClient;
import dev.meirong.shop.buyerbff.client.SearchServiceClient;
import dev.meirong.shop.buyerbff.client.WalletServiceClient;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import java.io.IOException;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BuyerClientProperties.class)
public class BuyerBffConfig {

    @Bean
    JdkClientHttpRequestFactory jdkClientHttpRequestFactory(BuyerClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(properties.httpVersion())
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    @Bean
    RestClient.Builder restClientBuilder(JdkClientHttpRequestFactory jdkClientHttpRequestFactory) {
        return RestClient.builder()
                .requestFactory(jdkClientHttpRequestFactory);
    }

    // ── Service proxy beans ──

    @Bean
    SearchServiceClient searchServiceClient(BuyerClientProperties properties,
                                            JdkClientHttpRequestFactory factory,
                                            ObjectMapper objectMapper) {
        return createServiceProxy(factory, properties.searchServiceUrl(), objectMapper, SearchServiceClient.class);
    }

    @Bean
    ProfileServiceClient profileServiceClient(BuyerClientProperties properties,
                                              JdkClientHttpRequestFactory factory,
                                              ObjectMapper objectMapper) {
        return createServiceProxy(factory, properties.profileServiceUrl(), objectMapper, ProfileServiceClient.class);
    }

    @Bean
    ProfileInternalServiceClient profileInternalServiceClient(BuyerClientProperties properties,
                                                              JdkClientHttpRequestFactory factory,
                                                              ObjectMapper objectMapper) {
        return createServiceProxy(factory, properties.profileServiceUrl(), objectMapper, ProfileInternalServiceClient.class);
    }

    @Bean
    WalletServiceClient walletServiceClient(BuyerClientProperties properties,
                                            JdkClientHttpRequestFactory factory,
                                            ObjectMapper objectMapper) {
        return createServiceProxy(factory, properties.walletServiceUrl(), objectMapper, WalletServiceClient.class);
    }

    @Bean
    PromotionServiceClient promotionServiceClient(BuyerClientProperties properties,
                                                  JdkClientHttpRequestFactory factory,
                                                  ObjectMapper objectMapper) {
        return createServiceProxy(factory, properties.promotionServiceUrl(), objectMapper, PromotionServiceClient.class);
    }

    @Bean
    PromotionInternalServiceClient promotionInternalServiceClient(BuyerClientProperties properties,
                                                                  JdkClientHttpRequestFactory factory,
                                                                  ObjectMapper objectMapper) {
        return createServiceProxy(factory, properties.promotionServiceUrl(), objectMapper, PromotionInternalServiceClient.class);
    }

    @Bean
    MarketplaceServiceClient marketplaceServiceClient(BuyerClientProperties properties,
                                                      JdkClientHttpRequestFactory factory,
                                                      ObjectMapper objectMapper) {
        return createServiceProxy(factory, properties.marketplaceServiceUrl(), objectMapper, MarketplaceServiceClient.class);
    }

    @Bean
    OrderServiceClient orderServiceClient(BuyerClientProperties properties,
                                          JdkClientHttpRequestFactory factory,
                                          ObjectMapper objectMapper) {
        return createServiceProxy(factory, properties.orderServiceUrl(), objectMapper, OrderServiceClient.class);
    }

    @Bean
    LoyaltyServiceClient loyaltyServiceClient(BuyerClientProperties properties,
                                              JdkClientHttpRequestFactory factory,
                                              ObjectMapper objectMapper) {
        return createServiceProxy(factory, properties.loyaltyServiceUrl(), objectMapper, LoyaltyServiceClient.class);
    }

    // ── Helpers ──

    private <T> T createServiceProxy(JdkClientHttpRequestFactory factory,
                                     String baseUrl,
                                     ObjectMapper objectMapper,
                                     Class<T> clientClass) {
        RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultStatusHandler(HttpStatusCode::isError,
                        (request, response) -> handleDownstreamError(request, response, objectMapper))
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(clientClass);
    }

    private static void handleDownstreamError(HttpRequest request,
                                              ClientHttpResponse response,
                                              ObjectMapper objectMapper) throws IOException {
        CommonErrorCode errorCode = CommonErrorCode.DOWNSTREAM_ERROR;
        String message = "Downstream request failed: " + request.getURI();
        try {
            byte[] body = response.getBody().readAllBytes();
            if (body.length > 0) {
                JsonNode errorBody = objectMapper.readTree(body);
                String code = errorBody.path("code").asText();
                String status = code.isBlank() ? errorBody.path("status").asText() : code;
                String downstreamMsg = errorBody.path("message").asText();
                if (downstreamMsg.isBlank()) {
                    downstreamMsg = errorBody.path("detail").asText();
                }
                if (!status.isBlank()) {
                    errorCode = resolveErrorCode(status);
                }
                if (!downstreamMsg.isBlank()) {
                    message = downstreamMsg;
                }
            }
        } catch (IOException ignored) {
            // keep default message
        }
        throw new BusinessException(errorCode, message);
    }

    private static CommonErrorCode resolveErrorCode(String code) {
        for (CommonErrorCode errorCode : CommonErrorCode.values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return CommonErrorCode.DOWNSTREAM_ERROR;
    }
}
