package dev.meirong.shop.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_delivery")
public class WebhookDeliveryEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_RETRYING = "RETRYING";

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "endpoint_id", nullable = false, length = 64)
    private String endpointId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookDeliveryEntity() {
    }

    public static WebhookDeliveryEntity create(String endpointId, String eventType,
                                                 String eventId, String payload) {
        WebhookDeliveryEntity entity = new WebhookDeliveryEntity();
        entity.id = UUID.randomUUID().toString();
        entity.endpointId = endpointId;
        entity.eventType = eventType;
        entity.eventId = eventId;
        entity.payload = payload;
        entity.status = STATUS_PENDING;
        entity.attemptCount = 0;
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

    public void markSuccess(int responseCode, String responseBody) {
        this.status = STATUS_SUCCESS;
        this.responseCode = responseCode;
        this.responseBody = truncate(responseBody, 2000);
        this.attemptCount++;
    }

    public void markFailed(int responseCode, String responseBody, int maxAttempts,
                            int retryIntervalSeconds) {
        this.attemptCount++;
        this.responseCode = responseCode;
        this.responseBody = truncate(responseBody, 2000);
        if (this.attemptCount >= maxAttempts) {
            this.status = STATUS_FAILED;
        } else {
            this.status = STATUS_RETRYING;
            // Exponential backoff: interval * 2^(attempt-1)
            long backoff = retryIntervalSeconds * (long) Math.pow(2, this.attemptCount - 1);
            this.nextRetryAt = Instant.now().plusSeconds(backoff);
        }
    }

    public void markFailedNoResponse(String error, int maxAttempts, int retryIntervalSeconds) {
        this.attemptCount++;
        this.responseBody = truncate(error, 2000);
        if (this.attemptCount >= maxAttempts) {
            this.status = STATUS_FAILED;
        } else {
            this.status = STATUS_RETRYING;
            long backoff = retryIntervalSeconds * (long) Math.pow(2, this.attemptCount - 1);
            this.nextRetryAt = Instant.now().plusSeconds(backoff);
        }
    }

    private String truncate(String s, int maxLen) {
        return (s != null && s.length() > maxLen) ? s.substring(0, maxLen) : s;
    }

    public String getId() { return id; }
    public String getEndpointId() { return endpointId; }
    public String getEventType() { return eventType; }
    public String getEventId() { return eventId; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public Integer getResponseCode() { return responseCode; }
    public String getResponseBody() { return responseBody; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
