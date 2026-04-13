package dev.meirong.shop.sellerbff.config;

import dev.meirong.shop.clients.marketplace.MarketplaceServiceClient;
import dev.meirong.shop.clients.order.OrderServiceClient;
import dev.meirong.shop.clients.profile.ProfileServiceClient;
import dev.meirong.shop.clients.promotion.PromotionServiceClient;
import dev.meirong.shop.clients.search.SearchServiceClient;
import dev.meirong.shop.clients.wallet.WalletServiceClient;
import dev.meirong.shop.httpclient.support.ShopHttpExchangeSupport;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SellerClientProperties.class)
public class SellerBffConfig {

    @Bean
    ProfileServiceClient profileServiceClient(ShopHttpExchangeSupport support,
                                              SellerClientProperties properties) {
        return support.createClient(properties.profileServiceUrl(), ProfileServiceClient.class);
    }

    @Bean
    MarketplaceServiceClient marketplaceServiceClient(ShopHttpExchangeSupport support,
                                                      SellerClientProperties properties) {
        return support.createClient(properties.marketplaceServiceUrl(), MarketplaceServiceClient.class);
    }

    @Bean
    OrderServiceClient orderServiceClient(ShopHttpExchangeSupport support,
                                          SellerClientProperties properties) {
        return support.createClient(properties.orderServiceUrl(), OrderServiceClient.class);
    }

    @Bean
    WalletServiceClient walletServiceClient(ShopHttpExchangeSupport support,
                                            SellerClientProperties properties) {
        return support.createClient(properties.walletServiceUrl(), WalletServiceClient.class);
    }

    @Bean
    PromotionServiceClient promotionServiceClient(ShopHttpExchangeSupport support,
                                                  SellerClientProperties properties) {
        return support.createClient(properties.promotionServiceUrl(), PromotionServiceClient.class);
    }

    @Bean
    SearchServiceClient searchServiceClient(ShopHttpExchangeSupport support,
                                            SellerClientProperties properties) {
        return support.createClient(properties.searchServiceUrl(), SearchServiceClient.class);
    }
}
