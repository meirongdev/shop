package dev.meirong.shop.authserver.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.auth")
public record AuthProperties(String issuer,
                             String secret,
                             Duration tokenTtl,
                             String googleClientId,
                             String appleClientId,
                             String profileServiceUrl,
                             String internalToken,
                             String userRegisteredTopic) {
}
