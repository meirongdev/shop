package dev.meirong.shop.activity.service;

import dev.meirong.shop.activity.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ActivityOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ActivityOutboxPublisher.class);

    private final ActivityOutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public ActivityOutboxPublisher(ActivityOutboxEventRepository outboxRepository,
                                    KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<ActivityOutboxEvent> events = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");
        for (ActivityOutboxEvent event : events) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getGameId(), event.getPayload());
                event.markPublished();
                outboxRepository.save(event);
                log.debug("Published event: topic={}, type={}", event.getTopic(), event.getEventType());
            } catch (RuntimeException exception) {
                log.error("Failed to publish event id={}", event.getId(), exception);
                break;
            }
        }
    }
}
