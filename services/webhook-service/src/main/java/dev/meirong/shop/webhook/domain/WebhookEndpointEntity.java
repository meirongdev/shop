package dev.meirong.shop.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "webhook_endpoint")
public class WebhookEndpointEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "seller_id", nullable = false, length = 64)
    private String sellerId;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(nullable = false, length = 256)
    private String secret;

    @Column(name = "event_types", nullable = false, length = 1024)
    private String eventTypes;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 256)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookEndpointEntity() {
    }

    public static WebhookEndpointEntity create(String sellerId, String url, String secret,
                                                 Set<String> eventTypes, String description) {
        WebhookEndpointEntity entity = new WebhookEndpointEntity();
        entity.id = UUID.randomUUID().toString();
        entity.sellerId = sellerId;
        entity.url = url;
        entity.secret = secret;
        entity.eventTypes = String.join(",", eventTypes);
        entity.active = true;
        entity.description = description;
        return entity;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void update(String url, Set<String> eventTypes, String description) {
        this.url = url;
        this.eventTypes = String.join(",", eventTypes);
        this.description = description;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public boolean matchesEventType(String eventType) {
        return active && getEventTypesSet().contains(eventType);
    }

    public Set<String> getEventTypesSet() {
        return Arrays.stream(eventTypes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public String getId() { return id; }
    public String getSellerId() { return sellerId; }
    public String getUrl() { return url; }
    public String getSecret() { return secret; }
    public String getEventTypes() { return eventTypes; }
    public boolean isActive() { return active; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
