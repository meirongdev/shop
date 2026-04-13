package dev.meirong.shop.search.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.search")
public record SearchProperties(
        MeilisearchProperties meilisearch,
        String productTopic,
        String marketplaceServiceUrl,
        AnalyticsProperties analytics
) {
    public record MeilisearchProperties(
            String url,
            String adminKey,
            String searchKey,
            Duration taskTimeout,
            Duration taskPollInterval
    ) {}

    public record AnalyticsProperties(
            Duration retention,
            int maxTrackedQueries,
            int minimumTrendingSearches
    ) {}
}
