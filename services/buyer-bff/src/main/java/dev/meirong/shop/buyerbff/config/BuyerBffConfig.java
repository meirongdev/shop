package dev.meirong.shop.buyerbff.config;

import dev.meirong.shop.clients.loyalty.LoyaltyServiceClient;
import dev.meirong.shop.clients.marketplace.MarketplaceServiceClient;
import dev.meirong.shop.clients.order.OrderServiceClient;
import dev.meirong.shop.clients.profile.ProfileInternalServiceClient;
import dev.meirong.shop.clients.profile.ProfileServiceClient;
import dev.meirong.shop.clients.promotion.PromotionInternalServiceClient;
import dev.meirong.shop.clients.promotion.PromotionServiceClient;
import dev.meirong.shop.clients.search.SearchServiceClient;
import dev.meirong.shop.clients.wallet.WalletServiceClient;
import dev.meirong.shop.httpclient.support.ShopHttpExchangeSupport;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BuyerClientProperties.class)
public class BuyerBffConfig {

    // ── Service proxy beans ──

    @Bean
    SearchServiceClient searchServiceClient(ShopHttpExchangeSupport support,
                                            BuyerClientProperties properties) {
        return support.createClient(properties.searchServiceUrl(), SearchServiceClient.class);
    }

    @Bean
    ProfileServiceClient profileServiceClient(ShopHttpExchangeSupport support,
                                              BuyerClientProperties properties) {
        return support.createClient(properties.profileServiceUrl(), ProfileServiceClient.class);
    }

    @Bean
    ProfileInternalServiceClient profileInternalServiceClient(ShopHttpExchangeSupport support,
                                                              BuyerClientProperties properties) {
        return support.createClient(properties.profileServiceUrl(), ProfileInternalServiceClient.class);
    }

    @Bean
    WalletServiceClient walletServiceClient(ShopHttpExchangeSupport support,
                                            BuyerClientProperties properties) {
        return support.createClient(properties.walletServiceUrl(), WalletServiceClient.class);
    }

    @Bean
    PromotionServiceClient promotionServiceClient(ShopHttpExchangeSupport support,
                                                  BuyerClientProperties properties) {
        return support.createClient(properties.promotionServiceUrl(), PromotionServiceClient.class);
    }

    @Bean
    PromotionInternalServiceClient promotionInternalServiceClient(ShopHttpExchangeSupport support,
                                                                  BuyerClientProperties properties) {
        return support.createClient(properties.promotionServiceUrl(), PromotionInternalServiceClient.class);
    }

    @Bean
    MarketplaceServiceClient marketplaceServiceClient(ShopHttpExchangeSupport support,
                                                      BuyerClientProperties properties) {
        return support.createClient(properties.marketplaceServiceUrl(), MarketplaceServiceClient.class);
    }

    @Bean
    OrderServiceClient orderServiceClient(ShopHttpExchangeSupport support,
                                          BuyerClientProperties properties) {
        return support.createClient(properties.orderServiceUrl(), OrderServiceClient.class);
    }

    @Bean
    LoyaltyServiceClient loyaltyServiceClient(ShopHttpExchangeSupport support,
                                              BuyerClientProperties properties) {
        return support.createClient(properties.loyaltyServiceUrl(), LoyaltyServiceClient.class);
    }
}
