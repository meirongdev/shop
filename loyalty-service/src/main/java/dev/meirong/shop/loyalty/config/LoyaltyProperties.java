package dev.meirong.shop.loyalty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.loyalty")
public record LoyaltyProperties(
        String orderEventsTopic,
        String buyerRegisteredTopic,
        String profileServiceUrl,
        int checkinBasePoints,
        int makeupCost,
        int maxMakeupPerMonth
) {
}
