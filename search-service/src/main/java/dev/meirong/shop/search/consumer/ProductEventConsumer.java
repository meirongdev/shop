package dev.meirong.shop.search.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.idempotency.IdempotencyExempt;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.MarketplaceProductEventData;
import dev.meirong.shop.search.index.ProductDocument;
import dev.meirong.shop.search.index.ProductIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
@IdempotencyExempt(reason = "Search indexing is replay-safe because index/remove converge on the same product document id")
public class ProductEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);

    private final ProductIndexer indexer;
    private final ObjectMapper objectMapper;

    public ProductEventConsumer(ProductIndexer indexer, ObjectMapper objectMapper) {
        this.indexer = indexer;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            dltTopicSuffix = ".dlq",
            autoCreateTopics = "true"
    )
    @KafkaListener(topics = "${shop.search.product-topic}", groupId = "search-service")
    public void consume(String message) throws Exception {
        var envelope = objectMapper.readValue(message,
                new TypeReference<EventEnvelope<MarketplaceProductEventData>>() {});
        var data = envelope.data();
        var doc = ProductDocument.fromEventData(data);

        switch (envelope.type()) {
            case "PRODUCT_CREATED", "PRODUCT_UPDATED", "PRODUCT_PUBLISHED" -> {
                if (data.published()) {
                    indexer.index(doc);
                    log.info("Indexed product {} (event: {})", data.productId(), envelope.type());
                } else {
                    indexer.remove(data.productId());
                    log.info("Skipped unpublished product {} (event: {})", data.productId(), envelope.type());
                }
            }
            case "PRODUCT_DELETED", "PRODUCT_UNPUBLISHED" -> {
                indexer.remove(data.productId());
                log.info("Removed product {} from index (event: {})", data.productId(), envelope.type());
            }
            default -> log.warn("Unknown event type: {}", envelope.type());
        }
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("Message sent to DLQ: {}", message);
    }
}
