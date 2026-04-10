package dev.meirong.shop.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.meirong.shop.contracts.search.SearchApi;
import dev.meirong.shop.search.index.ProductIndexSettings;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.SearchResultPaginated;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

    @Mock
    private Client searchClient;

    @Mock
    private Index index;

    @Mock
    private SearchResultPaginated searchResult;

    @Mock
    private SearchQueryAnalyticsService analyticsService;

    private SimpleMeterRegistry meterRegistry;
    private ProductSearchService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new ProductSearchService(searchClient, analyticsService, meterRegistry);
        when(searchClient.index(ProductIndexSettings.INDEX_NAME)).thenReturn(index);
    }

    @Test
    void search_recordsSuccessCounterAndTimer() throws Exception {
        when(index.search(any(SearchRequest.class))).thenReturn(searchResult);
        HashMap<String, Object> hit = new HashMap<>();
        hit.put("id", "prod-1");
        hit.put("sellerId", "seller-1");
        hit.put("name", "Alpha Serum");
        hit.put("description", "Hydrating serum");
        hit.put("priceInCents", 1999);
        hit.put("inventory", 12);
        hit.put("categoryId", "cat-1");
        hit.put("categoryName", "Serum");
        hit.put("imageUrl", "https://example.com/prod-1.png");
        hit.put("status", "PUBLISHED");
        ArrayList<HashMap<String, Object>> hits = new ArrayList<>();
        hits.add(hit);
        when(searchResult.getHits()).thenReturn(hits);
        when(searchResult.getTotalHits()).thenReturn(1);
        when(searchResult.getPage()).thenReturn(1);
        when(searchResult.getTotalPages()).thenReturn(1);
        when(searchResult.getFacetDistribution()).thenReturn(Map.of());

        SearchApi.SearchProductsResponse response =
                service.search(new SearchApi.SearchProductsRequest("alpha", null, null, 1, 20, List.of("en")));

        assertThat(response.hits()).hasSize(1);
        assertThat(meterRegistry.get("shop_search_query_total")
                .tag("service", "search-service")
                .tag("operation", "search")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shop_search_query_duration_seconds")
                .tag("service", "search-service")
                .tag("operation", "search")
                .tag("result", "success")
                .timer()
                .count()).isEqualTo(1);
        verify(analyticsService).recordQuery("alpha");
    }

    @Test
    void search_recordsFailureMetrics() throws Exception {
        when(index.search(any(SearchRequest.class))).thenThrow(new IllegalStateException("search unavailable"));

        assertThatThrownBy(() ->
                service.search(new SearchApi.SearchProductsRequest("alpha", null, null, 1, 20, List.of("en"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("search unavailable");

        assertThat(meterRegistry.get("shop_search_query_total")
                .tag("service", "search-service")
                .tag("operation", "search")
                .tag("result", "failure")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("shop_search_query_duration_seconds")
                .tag("service", "search-service")
                .tag("operation", "search")
                .tag("result", "failure")
                .timer()
                .count()).isEqualTo(1);
    }
}
