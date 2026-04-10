package dev.meirong.shop.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.meirong.shop.contracts.marketplace.MarketplaceApi;
import dev.meirong.shop.contracts.marketplace.MarketplaceInternalApi;
import dev.meirong.shop.search.client.MarketplaceInternalClient;
import dev.meirong.shop.search.index.ProductIndexSettings;
import dev.meirong.shop.search.index.ProductIndexer;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.model.SwapIndexesParams;
import com.meilisearch.sdk.model.TaskInfo;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReindexServiceTest {

    @Mock
    private Client adminClient;

    @Mock
    private MarketplaceInternalClient marketplaceClient;

    @Mock
    private ProductIndexer indexer;

    @Mock
    private ProductIndexSettings productIndexSettings;

    private ReindexService reindexService;

    @BeforeEach
    void setUp() {
        reindexService = new ReindexService(adminClient, marketplaceClient, indexer, productIndexSettings);
    }

    @Test
    void reindex_waitsForSwapAndDeleteTasks() throws Exception {
        var product = new MarketplaceApi.ProductResponse(
                UUID.randomUUID(),
                "seller-1",
                "SKU-1",
                "Ranking Serum",
                "ranking test product",
                new BigDecimal("19.99"),
                10,
                true,
                null,
                null,
                null,
                "PUBLISHED",
                0,
                BigDecimal.ZERO
        );
        when(marketplaceClient.fetchProducts(0, 500))
                .thenReturn(new MarketplaceInternalApi.PagedProductsResponse(List.of(product), 0, 1, 1));

        TaskInfo swapTask = taskInfo(101);
        TaskInfo deleteTask = taskInfo(102);
        when(adminClient.swapIndexes(any(SwapIndexesParams[].class))).thenReturn(swapTask);
        when(adminClient.deleteIndex(any())).thenReturn(deleteTask);

        reindexService.reindex();

        ArgumentCaptor<String> tempIndexCaptor = ArgumentCaptor.forClass(String.class);
        verify(productIndexSettings).ensureIndex(tempIndexCaptor.capture());
        String tempIndex = tempIndexCaptor.getValue();

        assertThat(tempIndex).startsWith("products_");
        verify(indexer).indexBatch(any(), any());
        verify(adminClient).swapIndexes(any(SwapIndexesParams[].class));
        verify(adminClient).deleteIndex(tempIndex);
        verify(adminClient).waitForTask(101);
        verify(adminClient).waitForTask(102);
    }

    private static TaskInfo taskInfo(int taskUid) throws Exception {
        TaskInfo taskInfo = new TaskInfo();
        Field field = TaskInfo.class.getDeclaredField("taskUid");
        field.setAccessible(true);
        field.set(taskInfo, taskUid);
        return taskInfo;
    }
}
