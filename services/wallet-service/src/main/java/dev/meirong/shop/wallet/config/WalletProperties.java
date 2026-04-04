package dev.meirong.shop.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.wallet")
public record WalletProperties(boolean stripeEnabled,
                               String stripeSecretKey,
                               String stripePublicKey,
                               boolean paypalEnabled,
                               String paypalClientId,
                               String paypalClientSecret,
                               String paypalBaseUrl,
                               boolean klarnaEnabled,
                               String klarnaUsername,
                               String klarnaPassword,
                               String klarnaBaseUrl,
                               String defaultCurrency,
                               String walletTopic) {
}
