package dev.meirong.shop.search.config;

import dev.meirong.shop.search.client.MarketplaceInternalExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
public class SearchHttpClientConfig {

    @Bean
    MarketplaceInternalExchange marketplaceInternalExchange(SearchProperties properties) {
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.marketplaceServiceUrl())
                .defaultHeader("X-Internal-Token", properties.internalToken())
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(MarketplaceInternalExchange.class);
    }
}
