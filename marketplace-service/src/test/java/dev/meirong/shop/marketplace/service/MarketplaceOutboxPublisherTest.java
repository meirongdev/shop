package dev.meirong.shop.marketplace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.meirong.shop.marketplace.domain.MarketplaceOutboxEventEntity;
import dev.meirong.shop.marketplace.domain.MarketplaceOutboxEventRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class MarketplaceOutboxPublisherTest {

    @Mock
    private MarketplaceOutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private MarketplaceOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new MarketplaceOutboxPublisher(repository, kafkaTemplate);
    }

    @Test
    void publishPendingEvents_marksEventPublishedAfterKafkaAck() {
        MarketplaceOutboxEventEntity event = new MarketplaceOutboxEventEntity(
                "product-1",
                "marketplace.product.events.v1",
                MarketplaceEventType.PRODUCT_CREATED.name(),
                "{}"
        );
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);

        when(repository.findTop100ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        publisher.publishPendingEvents();

        assertThat(event.isPublished()).isTrue();
        verify(repository).saveAll(List.of(event));
    }

    @Test
    void publishPendingEvents_sendFailureDoesNotMarkEventPublished() {
        MarketplaceOutboxEventEntity event = new MarketplaceOutboxEventEntity(
                "product-2",
                "marketplace.product.events.v1",
                MarketplaceEventType.PRODUCT_UPDATED.name(),
                "{}"
        );
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("broker unavailable"));

        when(repository.findTop100ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

        assertThatThrownBy(() -> publisher.publishPendingEvents())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish marketplace outbox event");
        assertThat(event.isPublished()).isFalse();
        verify(repository, never()).saveAll(any());
    }
}
