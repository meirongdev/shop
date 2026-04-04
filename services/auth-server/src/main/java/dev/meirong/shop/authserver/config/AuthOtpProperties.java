package dev.meirong.shop.authserver.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.auth-otp")
public record AuthOtpProperties(Duration codeTtl,
                                Duration cooldownTtl,
                                Duration lockoutTtl,
                                int maxAttempts,
                                int dailyLimit) {
}
