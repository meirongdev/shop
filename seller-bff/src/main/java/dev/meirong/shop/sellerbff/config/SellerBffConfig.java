package dev.meirong.shop.sellerbff.config;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SellerClientProperties.class)
public class SellerBffConfig {

    @Bean
    RestClient.Builder restClientBuilder(SellerClientProperties properties) {
        return RestClient.builder()
                .requestFactory(requestFactory(properties));
    }

    @Bean
    RestClient searchRestClient(SellerClientProperties properties) {
        return RestClient.builder()
                .requestFactory(requestFactory(properties))
                .baseUrl(properties.searchServiceUrl())
                .defaultHeader("X-Internal-Token", properties.internalToken())
                .build();
    }

    private ClientHttpRequestFactory requestFactory(SellerClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
        factory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
        return factory;
    }
}
