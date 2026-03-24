package dev.meirong.shop.wallet.service;

import dev.meirong.shop.wallet.domain.WalletOutboxEventEntity;
import dev.meirong.shop.wallet.domain.WalletOutboxEventRepository;
import java.util.List;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WalletOutboxPublisher {

    private final WalletOutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public WalletOutboxPublisher(WalletOutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${shop.wallet.outbox-publish-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<WalletOutboxEventEntity> events = repository.findTop20ByPublishedFalseOrderByCreatedAtAsc();
        for (WalletOutboxEventEntity event : events) {
            kafkaTemplate.send(event.getTopic(), event.getPayload());
            event.markPublished();
        }
        repository.saveAll(events);
    }
}
