package dev.meirong.shop.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.subscription")
public record SubscriptionProperties(
        String orderServiceUrl,
        String renewalCron,
        String eventsTopic
) {
}
