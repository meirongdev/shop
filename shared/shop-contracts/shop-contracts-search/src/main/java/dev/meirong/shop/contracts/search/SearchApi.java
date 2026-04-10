package dev.meirong.shop.contracts.search;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SearchApi {

    public static final String BASE_PATH = "/search/v1";
    public static final String SEARCH_PRODUCTS = BASE_PATH + "/products";
    public static final String SEARCH_PRODUCT_SUGGESTIONS = BASE_PATH + "/products/suggestions";
    public static final String TRENDING_QUERIES = BASE_PATH + "/queries/trending";
    public static final String REINDEX_PRODUCTS = BASE_PATH + "/products/_reindex";
    public static final String HEALTH = BASE_PATH + "/health";

    private SearchApi() {}

    public record SearchProductsRequest(
            String q,
            String categoryId,
            String sort,
            int page,
            int hitsPerPage,
            List<String> locales
    ) {
        public SearchProductsRequest(String q, String categoryId, String sort, int page, int hitsPerPage) {
            this(q, categoryId, sort, page, hitsPerPage, List.of());
        }

        public SearchProductsRequest {
            locales = locales == null ? List.of() : List.copyOf(locales);
        }
    }

    public record SearchProductsResponse(
            List<ProductHit> hits,
            long totalHits,
            int page,
            int totalPages,
            Map<String, Map<String, Integer>> facetDistribution
    ) {}

    public record SearchSuggestionsResponse(List<SearchSuggestion> suggestions) {}

    public record TrendingQueriesResponse(List<TrendingQuery> queries) {}

    public record ProductHit(
            String id,
            String sellerId,
            String name,
            String description,
            BigDecimal price,
            int inventory,
            String categoryId,
            String categoryName,
            String imageUrl,
            String status
    ) {}

    public record SearchSuggestion(
            String productId,
            String name,
            String categoryName
    ) {}

    public record TrendingQuery(
            String query,
            long searches,
            Instant lastSearchedAt
    ) {}
}
