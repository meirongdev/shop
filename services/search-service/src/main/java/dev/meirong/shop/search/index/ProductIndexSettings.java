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

    private final Client adminClient;
    private final ReindexService reindexService;

    public ProductIndexSettings(@Qualifier("meilisearchAdminClient") Client adminClient,
                                @Lazy ReindexService reindexService) {
        this.adminClient = adminClient;
        this.reindexService = reindexService;
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
            waitForTask(adminClient.createIndex(indexName, "id"));
        } catch (RuntimeException exception) {
            log.debug("Index '{}' may already exist: {}", indexName, exception.getMessage());
        }

        var index = adminClient.index(indexName);
        waitForTask(index.updateSearchableAttributesSettings(new String[]{"name", "description", "categoryName"}));
        waitForTask(index.updateFilterableAttributesSettings(new String[]{"categoryId", "sellerId", "published", "priceInCents"}));
        waitForTask(index.updateSortableAttributesSettings(new String[]{"priceInCents", "createdAt", "name", "inventory"}));
        waitForTask(index.updateRankingRulesSettings(new String[]{
                "words",
                "typo",
                "proximity",
                "attribute",
                "sort",
                "exactness",
                "inventory:desc"
        }));
        waitForTask(index.updateLocalizedAttributesSettings(new LocalizedAttribute[]{
                new LocalizedAttribute(
                        new String[]{"name", "description", "categoryName"},
                        new String[]{"en", "zh", "ja"})
        }));

        var faceting = new Faceting();
        faceting.setMaxValuesPerFacet(100);
        waitForTask(index.updateFacetingSettings(faceting));

        var pagination = new Pagination();
        pagination.setMaxTotalHits(5000);
        waitForTask(index.updatePaginationSettings(pagination));

        var typoTolerance = new TypoTolerance();
        typoTolerance.setEnabled(true);
        waitForTask(index.updateTypoToleranceSettings(typoTolerance));
    }

    private void waitForTask(TaskInfo taskInfo) {
        adminClient.waitForTask(taskInfo.getTaskUid());
    }
}
