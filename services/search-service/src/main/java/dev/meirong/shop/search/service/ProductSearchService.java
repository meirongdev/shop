package dev.meirong.shop.search.service;

import dev.meirong.shop.contracts.search.SearchApi;
import dev.meirong.shop.search.index.ProductIndexSettings;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.MatchingStrategy;
import com.meilisearch.sdk.model.SearchResult;
import com.meilisearch.sdk.model.SearchResultPaginated;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchService {

    private final Client searchClient;
    private final SearchQueryAnalyticsService analyticsService;
    private final MeterRegistry meterRegistry;

    public ProductSearchService(@Qualifier("meilisearchSearchClient") Client searchClient,
                                SearchQueryAnalyticsService analyticsService,
                                MeterRegistry meterRegistry) {
        this.searchClient = searchClient;
        this.analyticsService = analyticsService;
        this.meterRegistry = meterRegistry;
    }

    public SearchApi.SearchProductsResponse search(SearchApi.SearchProductsRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "success";
        int page = request.page() <= 0 ? 1 : request.page();
        int hitsPerPage = clamp(request.hitsPerPage(), 1, 100);
        try {
            var searchRequest = new SearchRequest(normalizeQuery(request.q()))
                    .setPage(page)
                    .setHitsPerPage(hitsPerPage)
                    .setFacets(new String[]{"categoryId"});
            applyLocales(searchRequest, request.locales());

            if (request.categoryId() != null && !request.categoryId().isBlank()) {
                searchRequest.setFilter(new String[]{"categoryId = '" + request.categoryId() + "'"});
            }
            if (request.sort() != null && !request.sort().isBlank()) {
                searchRequest.setSort(new String[]{request.sort()});
            }

            var searchable = searchClient.index(ProductIndexSettings.INDEX_NAME).search(searchRequest);
            SearchApi.SearchProductsResponse response = toSearchResponse(searchable, page, hitsPerPage);
            analyticsService.recordQuery(request.q());
            return response;
        } catch (RuntimeException exception) {
            result = "failure";
            throw exception;
        } finally {
            searchCounter(result).increment();
            sample.stop(searchTimer(result));
        }
    }

    public SearchApi.SearchSuggestionsResponse suggest(String query, int requestedLimit, List<String> locales) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null) {
            return new SearchApi.SearchSuggestionsResponse(List.of());
        }

        int limit = clamp(requestedLimit, 1, 10);
        var request = new SearchRequest(normalizedQuery)
                .setLimit(limit * 3)
                .setAttributesToRetrieve(new String[]{"id", "name", "categoryName"})
                .setAttributesToSearchOn(new String[]{"name", "categoryName"})
                .setMatchingStrategy(MatchingStrategy.LAST);
        applyLocales(request, locales);

        var searchable = searchClient.index(ProductIndexSettings.INDEX_NAME).search(request);
        var suggestions = searchable.getHits().stream()
                .map(this::toSuggestion)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(
                                suggestion -> (suggestion.name() + "|" + suggestion.categoryName()).toLowerCase(Locale.ROOT),
                                suggestion -> suggestion,
                                (left, right) -> left,
                                LinkedHashMap::new),
                        deduplicated -> deduplicated.values().stream().limit(limit).toList()));

        return new SearchApi.SearchSuggestionsResponse(suggestions);
    }

    public SearchApi.TrendingQueriesResponse trending(int requestedLimit) {
        return analyticsService.trending(requestedLimit);
    }

    private SearchApi.SearchProductsResponse toSearchResponse(Object searchable, int page, int hitsPerPage) {
        if (searchable instanceof SearchResultPaginated result) {
            return new SearchApi.SearchProductsResponse(
                    result.getHits().stream().map(this::toProductHit).toList(),
                    result.getTotalHits(),
                    result.getPage(),
                    result.getTotalPages(),
                    normalizeFacets(result.getFacetDistribution())
            );
        }
        if (searchable instanceof SearchResult result) {
            long totalHits = result.getEstimatedTotalHits();
            int totalPages = totalHits == 0 ? 0 : (int) Math.ceil((double) totalHits / hitsPerPage);
            return new SearchApi.SearchProductsResponse(
                    result.getHits().stream().map(this::toProductHit).toList(),
                    totalHits,
                    page,
                    totalPages,
                    normalizeFacets(result.getFacetDistribution())
            );
        }
        throw new IllegalStateException("Unsupported Meilisearch response type: " + searchable.getClass().getName());
    }

    private SearchApi.ProductHit toProductHit(Map<String, Object> map) {
        long priceInCents = map.get("priceInCents") instanceof Number n ? n.longValue() : 0L;
        BigDecimal price = BigDecimal.valueOf(priceInCents, 2);
        int inventory = map.get("inventory") instanceof Number n ? n.intValue() : 0;
        return new SearchApi.ProductHit(
                (String) map.get("id"),
                (String) map.get("sellerId"),
                (String) map.get("name"),
                (String) map.get("description"),
                price,
                inventory,
                (String) map.get("categoryId"),
                (String) map.get("categoryName"),
                (String) map.get("imageUrl"),
                (String) map.get("status")
        );
    }

    private SearchApi.SearchSuggestion toSuggestion(Map<String, Object> map) {
        String productId = map.get("id") instanceof String value ? value : null;
        String name = map.get("name") instanceof String value ? value : null;
        String categoryName = map.get("categoryName") instanceof String value ? value : "";
        if (productId == null || name == null || name.isBlank()) {
            return null;
        }
        return new SearchApi.SearchSuggestion(productId, name, categoryName);
    }

    private Map<String, Map<String, Integer>> normalizeFacets(Object rawFacetDistribution) {
        if (!(rawFacetDistribution instanceof Map<?, ?> rawFacets)) {
            return Map.of();
        }
        Map<String, Map<String, Integer>> normalizedFacets = new HashMap<>();
        for (Map.Entry<?, ?> facetEntry : rawFacets.entrySet()) {
            if (!(facetEntry.getKey() instanceof String facetName) || !(facetEntry.getValue() instanceof Map<?, ?> rawValues)) {
                continue;
            }
            Map<String, Integer> values = new HashMap<>();
            for (Map.Entry<?, ?> valueEntry : rawValues.entrySet()) {
                if (valueEntry.getKey() instanceof String valueName && valueEntry.getValue() instanceof Number count) {
                    values.put(valueName, count.intValue());
                }
            }
            normalizedFacets.put(facetName, values);
        }
        return normalizedFacets;
    }

    private void applyLocales(SearchRequest searchRequest, List<String> locales) {
        if (locales == null || locales.isEmpty()) {
            return;
        }
        var sanitizedLocales = locales.stream()
                .filter(locale -> locale != null && !locale.isBlank())
                .distinct()
                .toList();
        if (!sanitizedLocales.isEmpty()) {
            searchRequest.setLocales(sanitizedLocales.toArray(String[]::new));
        }
    }

    private static String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private Counter searchCounter(String result) {
        return Counter.builder("shop_search_query_total")
                .tag("service", "search-service")
                .tag("operation", "search")
                .tag("result", result)
                .register(meterRegistry);
    }

    private Timer searchTimer(String result) {
        return Timer.builder("shop_search_query_duration_seconds")
                .tag("service", "search-service")
                .tag("operation", "search")
                .tag("result", result)
                .register(meterRegistry);
    }
}
