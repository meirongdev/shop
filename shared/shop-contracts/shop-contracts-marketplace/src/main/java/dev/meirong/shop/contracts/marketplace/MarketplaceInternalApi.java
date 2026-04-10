package dev.meirong.shop.contracts.marketplace;

import java.util.List;

public final class MarketplaceInternalApi {

    public static final String BASE_PATH = "/marketplace/internal";
    public static final String LIST_ALL_PRODUCTS = BASE_PATH + "/products";

    private MarketplaceInternalApi() {}

    public record PagedProductsResponse(
            List<MarketplaceApi.ProductResponse> products,
            int page,
            int totalPages,
            long totalElements
    ) {}
}
