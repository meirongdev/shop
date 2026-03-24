package dev.meirong.shop.webhook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.webhook")
public record WebhookProperties(
        String orderEventsTopic,
        String walletTransactionsTopic,
        String userRegisteredTopic,
        int deliveryTimeoutSeconds,
        int retryMaxAttempts,
        int retryIntervalSeconds
) {
}
