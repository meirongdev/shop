package dev.meirong.shop.contracts.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketplaceProductEventData(
        String productId,
        String sellerId,
        String sku,
        String name,
        String description,
        BigDecimal price,
        Integer inventory,
        boolean published,
        String categoryId,
        String categoryName,
        String imageUrl,
        String status,
        Instant occurredAt
) {
}
