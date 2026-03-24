package dev.meirong.shop.search.index;

import dev.meirong.shop.contracts.api.MarketplaceApi;
import dev.meirong.shop.contracts.event.MarketplaceProductEventData;
import java.time.Instant;

public record ProductDocument(
        String id,
        String sellerId,
        String sku,
        String name,
        String description,
        long priceInCents,
        int inventory,
        boolean published,
        String categoryId,
        String categoryName,
        String imageUrl,
        String status,
        Instant createdAt
) {
    public static ProductDocument fromEventData(MarketplaceProductEventData data) {
        return new ProductDocument(
                data.productId(),
                data.sellerId(),
                data.sku(),
                data.name(),
                data.description(),
                data.price().movePointRight(2).longValue(),
                data.inventory(),
                data.published(),
                data.categoryId(),
                data.categoryName(),
                data.imageUrl(),
                data.status(),
                data.occurredAt()
        );
    }

    public static ProductDocument fromProductResponse(MarketplaceApi.ProductResponse product) {
        return new ProductDocument(
                product.id().toString(),
                product.sellerId(),
                product.sku(),
                product.name(),
                product.description(),
                product.price().movePointRight(2).longValue(),
                product.inventory(),
                product.published(),
                product.categoryId(),
                product.categoryName(),
                product.imageUrl(),
                product.status(),
                Instant.now()
        );
    }
}
