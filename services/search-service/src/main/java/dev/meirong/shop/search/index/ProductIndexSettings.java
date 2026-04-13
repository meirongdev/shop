package dev.meirong.shop.search.index;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.model.LocalizedAttribute;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.Faceting;
import com.meilisearch.sdk.model.Pagination;
import com.meilisearch.sdk.model.TypoTolerance;
import dev.meirong.shop.search.service.ReindexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class ProductIndexSettings {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexSettings.class);

    public static final String INDEX_NAME = "products";

    private final ReindexService reindexService;
    private final MeilisearchTaskAwaiter taskAwaiter;
    private final Client adminClient;

    public ProductIndexSettings(@Qualifier("meilisearchAdminClient") Client adminClient,
                                @Lazy ReindexService reindexService,
                                MeilisearchTaskAwaiter taskAwaiter) {
        this.adminClient = adminClient;
        this.reindexService = reindexService;
        this.taskAwaiter = taskAwaiter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeIndex() {
        ensureIndex(INDEX_NAME);
        log.info("Meilisearch index '{}' settings initialized", INDEX_NAME);

        // Perform initial full sync from marketplace service to MeiliSearch.
        // This seeds the index with Flyway-inserted products that never emitted Kafka events.
        log.info("Starting initial product index sync from marketplace service...");
        try {
            reindexService.reindex();
        } catch (Exception e) {
            log.warn("Initial sync failed (marketplace may be starting up). Will rely on Kafka events for eventual consistency.", e.getMessage());
        }
    }

    public void ensureIndex(String indexName) {
        try {
            taskAwaiter.await(adminClient.createIndex(indexName, "id"));
        } catch (RuntimeException exception) {
            log.debug("Index '{}' may already exist: {}", indexName, exception.getMessage());
        }

        var index = adminClient.index(indexName);
        taskAwaiter.await(index.updateSearchableAttributesSettings(new String[]{"name", "description", "categoryName"}));
        taskAwaiter.await(index.updateFilterableAttributesSettings(
                new String[]{"categoryId", "sellerId", "published", "priceInCents"}));
        taskAwaiter.await(index.updateSortableAttributesSettings(
                new String[]{"priceInCents", "createdAt", "name", "inventory"}));
        taskAwaiter.await(index.updateRankingRulesSettings(new String[]{
                "words",
                "typo",
                "proximity",
                "attribute",
                "sort",
                "exactness",
                "inventory:desc"
        }));
        taskAwaiter.await(index.updateLocalizedAttributesSettings(new LocalizedAttribute[]{
                new LocalizedAttribute(
                        new String[]{"name", "description", "categoryName"},
                        new String[]{"en", "zh", "ja"})
        }));

        var faceting = new Faceting();
        faceting.setMaxValuesPerFacet(100);
        taskAwaiter.await(index.updateFacetingSettings(faceting));

        var pagination = new Pagination();
        pagination.setMaxTotalHits(5000);
        taskAwaiter.await(index.updatePaginationSettings(pagination));

        var typoTolerance = new TypoTolerance();
        typoTolerance.setEnabled(true);
        taskAwaiter.await(index.updateTypoToleranceSettings(typoTolerance));
    }
}
