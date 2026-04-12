package dev.meirong.shop.authserver.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "shop.auth")
public record AuthProperties(String issuer,
                             Resource rsaPrivateKey,
                             Resource rsaPublicKey,
                             Duration tokenTtl,
                             String googleClientId,
                             String appleClientId,
                             String profileServiceUrl,
                             String buyerRegisteredTopic) {
}
