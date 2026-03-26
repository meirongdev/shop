package dev.meirong.shop.buyerbff.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.buyer")
public record BuyerClientProperties(String internalToken,
                                    String profileServiceUrl,
                                    String promotionServiceUrl,
                                    String walletServiceUrl,
                                    String marketplaceServiceUrl,
                                    String orderServiceUrl,
                                    String searchServiceUrl,
                                    String loyaltyServiceUrl,
                                    Duration guestCartTtl,
                                    HttpClient.Version httpVersion,
                                    Duration connectTimeout,
                                    Duration readTimeout) {

    public BuyerClientProperties {
        guestCartTtl = guestCartTtl == null ? Duration.ofHours(48) : guestCartTtl;
        httpVersion = httpVersion == null ? HttpClient.Version.HTTP_1_1 : httpVersion;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
    }
}
