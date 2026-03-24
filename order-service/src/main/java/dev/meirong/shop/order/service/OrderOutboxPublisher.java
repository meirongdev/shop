package dev.meirong.shop.order.service;

import dev.meirong.shop.order.domain.OrderOutboxEventEntity;
import dev.meirong.shop.order.domain.OrderOutboxEventRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxPublisher.class);

    private final OrderOutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderOutboxPublisher(OrderOutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${shop.order.outbox-publish-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OrderOutboxEventEntity> events = repository.findTop20ByPublishedFalseOrderByCreatedAtAsc();
        for (OrderOutboxEventEntity event : events) {
            kafkaTemplate.send(event.getTopic(), event.getPayload());
            event.markPublished();
            log.debug("Published order event: {} for order {}", event.getEventType(), event.getOrderId());
        }
        if (!events.isEmpty()) {
            repository.saveAll(events);
        }
    }
}
