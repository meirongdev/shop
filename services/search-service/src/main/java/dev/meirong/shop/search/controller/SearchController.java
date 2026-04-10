package dev.meirong.shop.search.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.feature.FeatureToggleService;
import dev.meirong.shop.contracts.search.SearchApi;
import dev.meirong.shop.search.config.SearchFeatureFlags;
import dev.meirong.shop.search.service.ProductSearchService;
import dev.meirong.shop.search.service.ReindexService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private final ProductSearchService searchService;
    private final ReindexService reindexService;
    private final FeatureToggleService featureToggleService;

    public SearchController(ProductSearchService searchService,
                            ReindexService reindexService,
                            FeatureToggleService featureToggleService) {
        this.searchService = searchService;
        this.reindexService = reindexService;
        this.featureToggleService = featureToggleService;
    }

    @GetMapping(SearchApi.SEARCH_PRODUCTS)
    public ApiResponse<SearchApi.SearchProductsResponse> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int hitsPerPage,
            @RequestParam(required = false) List<String> locales) {
        List<String> effectiveLocales = locales;
        if (locales != null && !locales.isEmpty()
                && !featureToggleService.isEnabled(SearchFeatureFlags.LOCALE_AWARE_SEARCH, true)) {
            effectiveLocales = List.of();
        }
        var request = new SearchApi.SearchProductsRequest(q, categoryId, sort, page, hitsPerPage, effectiveLocales);
        return ApiResponse.success(searchService.search(request));
    }

    @GetMapping(SearchApi.SEARCH_PRODUCT_SUGGESTIONS)
    public ApiResponse<SearchApi.SearchSuggestionsResponse> suggestProducts(
            @RequestParam String q,
            @RequestParam(defaultValue = "8") int limit,
            @RequestParam(required = false) List<String> locales) {
        featureToggleService.requireEnabled(
                SearchFeatureFlags.AUTOCOMPLETE,
                true,
                "Search autocomplete is disabled");
        return ApiResponse.success(searchService.suggest(q, limit, locales));
    }

    @GetMapping(SearchApi.TRENDING_QUERIES)
    public ApiResponse<SearchApi.TrendingQueriesResponse> trendingQueries(
            @RequestParam(defaultValue = "10") int limit) {
        featureToggleService.requireEnabled(
                SearchFeatureFlags.TRENDING,
                true,
                "Trending search queries are disabled");
        return ApiResponse.success(searchService.trending(limit));
    }

    @PostMapping(SearchApi.REINDEX_PRODUCTS)
    public ApiResponse<String> reindex() {
        reindexService.reindex();
        return ApiResponse.success("reindex complete");
    }

    @GetMapping(SearchApi.HEALTH)
    public ApiResponse<String> health() {
        return ApiResponse.success("ok");
    }
}
