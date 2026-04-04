package dev.meirong.shop.authserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.auth-sms")
public record AuthSmsProperties(String gateway,
                                String twilioAccountSid,
                                String twilioAuthToken,
                                String twilioFromNumber) {
}
