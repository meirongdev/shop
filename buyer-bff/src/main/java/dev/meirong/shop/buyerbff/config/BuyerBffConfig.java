package dev.meirong.shop.buyerbff.config;

import dev.meirong.shop.buyerbff.client.SearchServiceClient;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BuyerClientProperties.class)
public class BuyerBffConfig {

    @Bean
    RestClient.Builder restClientBuilder(BuyerClientProperties properties) {
        return RestClient.builder()
                .requestFactory(requestFactory(properties));
    }

    @Bean
    SearchServiceClient searchServiceClient(BuyerClientProperties properties) {
        RestClient searchRestClient = RestClient.builder()
                .requestFactory(requestFactory(properties))
                .baseUrl(properties.searchServiceUrl())
                .defaultHeader("X-Internal-Token", properties.internalToken())
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(searchRestClient))
                .build();
        return factory.createClient(SearchServiceClient.class);
    }

    private ClientHttpRequestFactory requestFactory(BuyerClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
        factory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
        return factory;
    }
}
