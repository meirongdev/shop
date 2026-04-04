package dev.meirong.shop.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.notification")
public record NotificationProperties(
        String buyerRegisteredTopic,
        String orderEventsTopic,
        String walletTransactionsTopic,
        int retryMaxAttempts,
        String mailFrom
) {
    public NotificationProperties {
        if (buyerRegisteredTopic == null) buyerRegisteredTopic = "buyer.registered.v1";
        if (orderEventsTopic == null) orderEventsTopic = "order.events.v1";
        if (walletTransactionsTopic == null) walletTransactionsTopic = "wallet.transactions.v1";
        if (retryMaxAttempts <= 0) retryMaxAttempts = 3;
        if (mailFrom == null) mailFrom = "noreply@shop.dev.meirong";
    }
}
