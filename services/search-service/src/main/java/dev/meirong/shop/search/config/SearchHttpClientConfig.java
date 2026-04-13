package dev.meirong.shop.search.config;

import dev.meirong.shop.httpclient.support.ShopHttpExchangeSupport;
import dev.meirong.shop.search.client.MarketplaceInternalExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SearchHttpClientConfig {

    @Bean
    MarketplaceInternalExchange marketplaceInternalExchange(ShopHttpExchangeSupport support,
                                                            SearchProperties properties) {
        return support.createClient(properties.marketplaceServiceUrl(), MarketplaceInternalExchange.class);
    }
}
