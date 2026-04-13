package dev.meirong.shop.buyerbff.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.buyer")
public record BuyerClientProperties(String profileServiceUrl,
                                    String promotionServiceUrl,
                                    String walletServiceUrl,
                                    String marketplaceServiceUrl,
                                    String orderServiceUrl,
                                    String searchServiceUrl,
                                    String loyaltyServiceUrl,
                                    Duration guestCartTtl) {

    public BuyerClientProperties {
        guestCartTtl = guestCartTtl == null ? Duration.ofHours(48) : guestCartTtl;
    }
}
