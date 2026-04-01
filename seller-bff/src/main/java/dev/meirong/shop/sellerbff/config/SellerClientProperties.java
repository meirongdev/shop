package dev.meirong.shop.sellerbff.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.seller")
public record SellerClientProperties(String profileServiceUrl,
                                     String promotionServiceUrl,
                                     String walletServiceUrl,
                                     String marketplaceServiceUrl,
                                     String orderServiceUrl,
                                     String searchServiceUrl,
                                     Duration connectTimeout,
                                     Duration readTimeout) {

    public SellerClientProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
    }
}
