package dev.meirong.shop.search.service;

import dev.meirong.shop.search.client.MarketplaceInternalClient;
import dev.meirong.shop.search.index.ProductDocument;
import dev.meirong.shop.search.index.ProductIndexSettings;
import dev.meirong.shop.search.index.ProductIndexer;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.model.SwapIndexesParams;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ReindexService {

    private static final Logger log = LoggerFactory.getLogger(ReindexService.class);

    private final Client adminClient;
    private final MarketplaceInternalClient marketplaceClient;
    private final ProductIndexer indexer;
    private final ProductIndexSettings productIndexSettings;

    public ReindexService(@Qualifier("meilisearchAdminClient") Client adminClient,
                          MarketplaceInternalClient marketplaceClient,
                          ProductIndexer indexer,
                          ProductIndexSettings productIndexSettings) {
        this.adminClient = adminClient;
        this.marketplaceClient = marketplaceClient;
        this.indexer = indexer;
        this.productIndexSettings = productIndexSettings;
    }

    public void reindex() {
        var tempIndex = "products_" + Instant.now().toEpochMilli();
        log.info("Starting full reindex into temp index: {}", tempIndex);

        // 1. Create temp index with settings
        productIndexSettings.ensureIndex(tempIndex);

        // 2. Paginate through all products
        int page = 0;
        long totalIndexed = 0;
        while (true) {
            var response = marketplaceClient.fetchProducts(page, 500);
            var docs = response.products().stream()
                    .filter(product -> product.published())
                    .map(ProductDocument::fromProductResponse)
                    .toList();
            if (!docs.isEmpty()) {
                indexer.indexBatch(tempIndex, docs);
                totalIndexed += docs.size();
            }
            page++;
            if (page >= response.totalPages()) break;
        }

        log.info("Indexed {} products into temp index {}", totalIndexed, tempIndex);

        // 3. Swap indexes atomically
        var swapParams = new SwapIndexesParams();
        swapParams.setIndexes(new String[]{ProductIndexSettings.INDEX_NAME, tempIndex});
        waitForTask(adminClient.swapIndexes(new SwapIndexesParams[]{swapParams}));

        // 4. Delete old (now temp-named) index
        waitForTask(adminClient.deleteIndex(tempIndex));

        log.info("Full reindex complete. Swapped {} <-> {}", ProductIndexSettings.INDEX_NAME, tempIndex);
    }

    private void waitForTask(com.meilisearch.sdk.model.TaskInfo taskInfo) {
        adminClient.waitForTask(taskInfo.getTaskUid());
    }
}
