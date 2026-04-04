package dev.meirong.shop.search.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.web.GlobalExceptionHandler;
import dev.meirong.shop.common.feature.FeatureToggleService;
import dev.meirong.shop.search.config.SearchFeatureFlags;
import dev.meirong.shop.contracts.api.SearchApi;
import dev.meirong.shop.search.service.ProductSearchService;
import dev.meirong.shop.search.service.ReindexService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchController.class)
@Import(GlobalExceptionHandler.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductSearchService searchService;

    @MockBean
    private ReindexService reindexService;

    @MockBean
    private FeatureToggleService featureToggleService;

    @BeforeEach
    void setUp() {
        when(featureToggleService.isEnabled(anyString(), anyBoolean()))
                .thenAnswer(invocation -> invocation.getArgument(1, Boolean.class));
    }

    @Test
    void suggestProducts_returnsAutocompleteSuggestions() throws Exception {
        when(searchService.suggest("alp", 5, List.of("en")))
                .thenReturn(new SearchApi.SearchSuggestionsResponse(List.of(
                        new SearchApi.SearchSuggestion("prod-1", "Alpha Phone", "Devices")
                )));

        mockMvc.perform(get(SearchApi.SEARCH_PRODUCT_SUGGESTIONS)
                        .param("q", "alp")
                        .param("limit", "5")
                        .param("locales", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.suggestions[0].productId").value("prod-1"))
                .andExpect(jsonPath("$.data.suggestions[0].name").value("Alpha Phone"));
    }

    @Test
    void suggestProducts_whenFeatureDisabled_returnsServiceUnavailable() throws Exception {
        doThrow(new BusinessException(CommonErrorCode.FEATURE_DISABLED, "Search autocomplete is disabled"))
                .when(featureToggleService)
                .requireEnabled(SearchFeatureFlags.AUTOCOMPLETE, true, "Search autocomplete is disabled");

        mockMvc.perform(get(SearchApi.SEARCH_PRODUCT_SUGGESTIONS)
                        .param("q", "alp"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SC_FEATURE_DISABLED"))
                .andExpect(jsonPath("$.message").value("Search autocomplete is disabled"));
    }

    @Test
    void trendingQueries_returnsTrackedQueries() throws Exception {
        when(searchService.trending(3))
                .thenReturn(new SearchApi.TrendingQueriesResponse(List.of(
                        new SearchApi.TrendingQuery("Alpha Phone", 4, Instant.parse("2026-03-22T00:00:00Z"))
                )));

        mockMvc.perform(get(SearchApi.TRENDING_QUERIES)
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.queries[0].query").value("Alpha Phone"))
                .andExpect(jsonPath("$.data.queries[0].searches").value(4));
    }

    @Test
    void trendingQueries_whenFeatureDisabled_returnsServiceUnavailable() throws Exception {
        doThrow(new BusinessException(CommonErrorCode.FEATURE_DISABLED, "Trending search queries are disabled"))
                .when(featureToggleService)
                .requireEnabled(SearchFeatureFlags.TRENDING, true, "Trending search queries are disabled");

        mockMvc.perform(get(SearchApi.TRENDING_QUERIES))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SC_FEATURE_DISABLED"))
                .andExpect(jsonPath("$.message").value("Trending search queries are disabled"));
    }

    @Test
    void reindex_returnsOk() throws Exception {
        doNothing().when(reindexService).reindex();

        mockMvc.perform(post(SearchApi.REINDEX_PRODUCTS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data").value("reindex complete"));
    }

    @Test
    void searchProducts_whenLocaleFeatureDisabled_ignoresLocales() throws Exception {
        when(featureToggleService.isEnabled(SearchFeatureFlags.LOCALE_AWARE_SEARCH, true)).thenReturn(false);
        when(searchService.search(new SearchApi.SearchProductsRequest("serum", null, null, 1, 20, List.of())))
                .thenReturn(new SearchApi.SearchProductsResponse(List.of(), 0, 1, 0, java.util.Map.of()));

        mockMvc.perform(get(SearchApi.SEARCH_PRODUCTS)
                        .param("q", "serum")
                        .param("locales", "ja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"));
    }
}
