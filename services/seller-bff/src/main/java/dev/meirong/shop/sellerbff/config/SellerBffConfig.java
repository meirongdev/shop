package dev.meirong.shop.sellerbff.config;

import dev.meirong.shop.sellerbff.client.MarketplaceServiceClient;
import dev.meirong.shop.sellerbff.client.OrderServiceClient;
import dev.meirong.shop.sellerbff.client.ProfileServiceClient;
import dev.meirong.shop.sellerbff.client.PromotionServiceClient;
import dev.meirong.shop.sellerbff.client.SearchServiceClient;
import dev.meirong.shop.sellerbff.client.WalletServiceClient;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SellerClientProperties.class)
public class SellerBffConfig {

    @Bean
    JdkClientHttpRequestFactory jdkClientHttpRequestFactory(SellerClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    @Bean
    ProfileServiceClient profileServiceClient(SellerClientProperties properties,
                                              JdkClientHttpRequestFactory factory) {
        return createClient(properties.profileServiceUrl(), factory, ProfileServiceClient.class);
    }

    @Bean
    MarketplaceServiceClient marketplaceServiceClient(SellerClientProperties properties,
                                                      JdkClientHttpRequestFactory factory) {
        return createClient(properties.marketplaceServiceUrl(), factory, MarketplaceServiceClient.class);
    }

    @Bean
    OrderServiceClient orderServiceClient(SellerClientProperties properties,
                                          JdkClientHttpRequestFactory factory) {
        return createClient(properties.orderServiceUrl(), factory, OrderServiceClient.class);
    }

    @Bean
    WalletServiceClient walletServiceClient(SellerClientProperties properties,
                                            JdkClientHttpRequestFactory factory) {
        return createClient(properties.walletServiceUrl(), factory, WalletServiceClient.class);
    }

    @Bean
    PromotionServiceClient promotionServiceClient(SellerClientProperties properties,
                                                  JdkClientHttpRequestFactory factory) {
        return createClient(properties.promotionServiceUrl(), factory, PromotionServiceClient.class);
    }

    @Bean
    SearchServiceClient searchServiceClient(SellerClientProperties properties,
                                            JdkClientHttpRequestFactory factory) {
        return createClient(properties.searchServiceUrl(), factory, SearchServiceClient.class);
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
}
