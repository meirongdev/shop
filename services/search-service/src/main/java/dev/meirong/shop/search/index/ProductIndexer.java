package dev.meirong.shop.search.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ProductIndexer {

    private final Client adminClient;
    private final ObjectMapper objectMapper;

    public ProductIndexer(@Qualifier("meilisearchAdminClient") Client adminClient,
                          ObjectMapper objectMapper) {
        this.adminClient = adminClient;
        this.objectMapper = objectMapper;
    }

    public void index(ProductDocument doc) {
        try {
            adminClient.index(ProductIndexSettings.INDEX_NAME)
                    .addDocuments(objectMapper.writeValueAsString(List.of(doc)), "id");
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize product document", e);
        }
    }

    public void remove(String productId) {
        adminClient.index(ProductIndexSettings.INDEX_NAME).deleteDocument(productId);
    }

    public void indexBatch(List<ProductDocument> docs) {
        indexBatch(ProductIndexSettings.INDEX_NAME, docs);
    }

    public void indexBatch(String indexName, List<ProductDocument> docs) {
        if (docs.isEmpty()) {
            return;
        }
        try {
            adminClient.index(indexName)
                    .addDocuments(objectMapper.writeValueAsString(docs), "id");
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize product documents", e);
        }
    }
}
