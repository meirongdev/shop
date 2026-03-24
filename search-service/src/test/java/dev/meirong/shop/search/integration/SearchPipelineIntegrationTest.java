package dev.meirong.shop.search.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.contracts.api.SearchApi;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.MarketplaceProductEventData;
import dev.meirong.shop.search.index.ProductIndexSettings;
import dev.meirong.shop.search.service.ProductSearchService;
import com.meilisearch.sdk.Client;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class SearchPipelineIntegrationTest {

    private static final String TOPIC = "marketplace.product.events.v1";

    @ServiceConnection
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.9.0"));

    @Container
    static GenericContainer<?> meilisearch = new GenericContainer<>(DockerImageName.parse("getmeili/meilisearch:v1.12"))
            .withExposedPorts(7700)
            .withEnv("MEILI_ENV", "development")
            .withEnv("MEILI_MASTER_KEY", "testMasterKey");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("shop.search.meilisearch.url",
                () -> "http://" + meilisearch.getHost() + ":" + meilisearch.getMappedPort(7700));
        registry.add("shop.search.meilisearch.admin-key", () -> "testMasterKey");
        registry.add("shop.search.meilisearch.search-key", () -> "testMasterKey");
        registry.add("shop.search.product-topic", () -> TOPIC);
        registry.add("shop.security.internal.enabled", () -> "false");
    }

    @Autowired
    private ProductSearchService productSearchService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("meilisearchAdminClient")
    private Client adminClient;

    @BeforeEach
    void cleanIndex() {
        try {
            adminClient.index(ProductIndexSettings.INDEX_NAME).deleteAllDocuments();
        } catch (Exception ignored) {
            // index may not be ready yet; tests rely on polling below
        }
    }

    @Test
    void fullPipeline_publishEvent_thenSearch() throws Exception {
        sendEvent("PRODUCT_CREATED", "prod-1", "Alpha Phone", true);

        SearchApi.SearchProductsResponse response = awaitSearch("Alpha Phone");

        assertThat(response.hits()).isNotEmpty();
        assertThat(response.hits().stream().anyMatch(hit -> "prod-1".equals(hit.id()))).isTrue();
    }

    @Test
    void fullPipeline_publishUnpublishEvent_thenSearchReturnsEmpty() throws Exception {
        sendEvent("PRODUCT_CREATED", "prod-2", "Beta Phone", true);
        awaitSearch("Beta Phone");

        sendEvent("PRODUCT_UNPUBLISHED", "prod-2", "Beta Phone", false);

        SearchApi.SearchProductsResponse response = awaitNoResults("Beta Phone");
        assertThat(response.hits()).isEmpty();
    }

    @Test
    void fullPipeline_unpublishedCreateEvent_thenSearchReturnsEmpty() throws Exception {
        sendEvent("PRODUCT_CREATED", "prod-3", "Gamma Phone", false);

        SearchApi.SearchProductsResponse response = awaitNoResults("Gamma Phone");

        assertThat(response.hits()).isEmpty();
    }

    @Test
    void autocomplete_returnsIndexedSuggestions() throws Exception {
        sendEvent("PRODUCT_CREATED", "prod-4", "Alpha Ampoule", true);

        SearchApi.SearchSuggestionsResponse response = awaitSuggestions("Alp", java.util.List.of("en"));

        assertThat(response.suggestions()).extracting(SearchApi.SearchSuggestion::name).contains("Alpha Ampoule");
    }

    @Test
    void trendingQueries_recordsRepeatedSearches() throws Exception {
        sendEvent("PRODUCT_CREATED", "prod-5", "Delta Serum 2026", true);

        awaitSearch("Delta Serum 2026");
        productSearchService.search(new SearchApi.SearchProductsRequest("Delta Serum 2026", null, null, 1, 20, java.util.List.of("en")));
        productSearchService.search(new SearchApi.SearchProductsRequest("Delta Serum 2026", null, null, 1, 20, java.util.List.of("en")));

        SearchApi.TrendingQueriesResponse response = productSearchService.trending(5);

        assertThat(response.queries()).anySatisfy(query -> {
            assertThat(query.query()).isEqualTo("Delta Serum 2026");
            assertThat(query.searches()).isGreaterThanOrEqualTo(2L);
        });
    }

    @Test
    void search_withLocales_returnsLocalizedHit() throws Exception {
        sendEvent("PRODUCT_CREATED", "prod-6", "抹茶セラム", true);

        SearchApi.SearchProductsResponse response = awaitSearch(
                new SearchApi.SearchProductsRequest("抹茶", null, null, 1, 20, java.util.List.of("ja")));

        assertThat(response.hits()).extracting(SearchApi.ProductHit::id).contains("prod-6");
    }

    @Test
    void search_withoutExplicitSort_prefersHigherInventory() throws Exception {
        Instant baseTime = Instant.parse("2026-03-22T00:00:00Z");
        sendEvent("PRODUCT_CREATED", "prod-rank-1", "Ranking Serum", true, 0, baseTime);
        sendEvent("PRODUCT_CREATED", "prod-rank-2", "Ranking Serum", true, 30, baseTime);

        SearchApi.SearchProductsResponse response = awaitSearch("Ranking Serum");

        assertThat(response.hits()).extracting(SearchApi.ProductHit::id)
                .containsSequence("prod-rank-2", "prod-rank-1");
    }

    @Test
    void search_withExplicitSort_stillHonorsRequestedSort() throws Exception {
        Instant baseTime = Instant.parse("2026-03-22T00:00:00Z");
        sendEvent("PRODUCT_CREATED", "prod-rank-3", "Sorted Serum", true, 30, baseTime, new BigDecimal("29.99"));
        sendEvent("PRODUCT_CREATED", "prod-rank-4", "Sorted Serum", true, 0, baseTime, new BigDecimal("9.99"));

        SearchApi.SearchProductsResponse response = awaitSearch(
                new SearchApi.SearchProductsRequest("Sorted Serum", null, "priceInCents:asc", 1, 20));

        assertThat(response.hits()).extracting(SearchApi.ProductHit::id)
                .containsSequence("prod-rank-4", "prod-rank-3");
    }

    private void sendEvent(String type, String productId, String name, boolean published) throws Exception {
        sendEvent(type, productId, name, published, 10, Instant.now(), new BigDecimal("19.99"));
    }

    private void sendEvent(String type, String productId, String name, boolean published, int inventory, Instant occurredAt)
            throws Exception {
        sendEvent(type, productId, name, published, inventory, occurredAt, new BigDecimal("19.99"));
    }

    private void sendEvent(String type, String productId, String name, boolean published,
                           int inventory, Instant occurredAt, BigDecimal price) throws Exception {
        MarketplaceProductEventData data = new MarketplaceProductEventData(
                productId,
                "seller-1",
                "SKU-" + productId,
                name,
                "desc-" + name,
                price,
                inventory,
                published,
                "cat-1",
                "Category 1",
                "https://example.com/" + productId + ".png",
                "ACTIVE",
                occurredAt
        );
        EventEnvelope<MarketplaceProductEventData> event = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                "marketplace-service",
                type,
                occurredAt,
                data
        );

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(TOPIC, productId, objectMapper.writeValueAsString(event))).get();
        }
    }

    private SearchApi.SearchProductsResponse awaitSearch(String query) throws InterruptedException {
        return awaitSearch(new SearchApi.SearchProductsRequest(query, null, null, 1, 20));
    }

    private SearchApi.SearchProductsResponse awaitSearch(SearchApi.SearchProductsRequest request) throws InterruptedException {
        SearchApi.SearchProductsResponse last = null;
        for (int i = 0; i < 40; i++) {
            try {
                last = productSearchService.search(request);
                if (last != null && !last.hits().isEmpty()) {
                    return last;
                }
            } catch (Exception ignored) {
                // consumer/index may still be initializing
            }
            Thread.sleep(500);
        }
        return last == null ? new SearchApi.SearchProductsResponse(java.util.List.of(), 0, 1, 0, java.util.Map.of()) : last;
    }

    private SearchApi.SearchProductsResponse awaitNoResults(String query) throws InterruptedException {
        SearchApi.SearchProductsResponse last = null;
        for (int i = 0; i < 40; i++) {
            try {
                last = productSearchService.search(new SearchApi.SearchProductsRequest(query, null, null, 1, 20));
                if (last != null && last.hits().isEmpty()) {
                    return last;
                }
            } catch (Exception ignored) {
                // consumer/index may still be initializing
            }
            Thread.sleep(500);
        }
        return last == null ? new SearchApi.SearchProductsResponse(java.util.List.of(), 0, 1, 0, java.util.Map.of()) : last;
    }

    private SearchApi.SearchSuggestionsResponse awaitSuggestions(String query, java.util.List<String> locales)
            throws InterruptedException {
        SearchApi.SearchSuggestionsResponse last = null;
        for (int i = 0; i < 40; i++) {
            try {
                last = productSearchService.suggest(query, 8, locales);
                if (last != null && !last.suggestions().isEmpty()) {
                    return last;
                }
            } catch (Exception ignored) {
                // consumer/index may still be initializing
            }
            Thread.sleep(500);
        }
        return last == null ? new SearchApi.SearchSuggestionsResponse(java.util.List.of()) : last;
    }
}
