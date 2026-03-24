package dev.meirong.shop.marketplace.service;

import dev.meirong.shop.marketplace.domain.MarketplaceOutboxEventEntity;
import dev.meirong.shop.marketplace.domain.MarketplaceOutboxEventRepository;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MarketplaceOutboxPublisher {

    private final MarketplaceOutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public MarketplaceOutboxPublisher(MarketplaceOutboxEventRepository repository,
                                      KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${shop.marketplace.outbox.publish-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<MarketplaceOutboxEventEntity> events = repository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
        for (MarketplaceOutboxEventEntity event : events) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload()).join();
            } catch (CompletionException exception) {
                throw new IllegalStateException("Failed to publish marketplace outbox event " + event.getId(), exception.getCause());
            }
            event.markPublished();
        }
        repository.saveAll(events);
    }
}
