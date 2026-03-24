package dev.meirong.shop.promotion.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox record for compensatable operations in promotion-service.
 * The scheduler retries PENDING tasks up to {@code maxRetries} times.
 */
@Entity
@Table(name = "compensation_task",
        indexes = @Index(name = "idx_comp_task_status_next", columnList = "status, next_retry_at"))
public class CompensationTaskEntity {

    public enum Status { PENDING, SUCCEEDED, FAILED }

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "task_type", nullable = false, length = 64)
    private String taskType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "next_retry_at", nullable = false)
    private Instant nextRetryAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CompensationTaskEntity() {}

    public CompensationTaskEntity(String taskType, String aggregateId, String payload) {
        this(taskType, aggregateId, payload, 5);
    }

    public CompensationTaskEntity(String taskType, String aggregateId, String payload, int maxRetries) {
        this.id = UUID.randomUUID().toString();
        this.taskType = taskType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = Status.PENDING;
        this.retryCount = 0;
        this.maxRetries = maxRetries;
        this.nextRetryAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markSucceeded() {
        this.status = Status.SUCCEEDED;
        this.updatedAt = Instant.now();
    }

    public void recordFailure(String error) {
        this.retryCount++;
        this.lastError = error != null && error.length() > 2000 ? error.substring(0, 2000) : error;
        this.updatedAt = Instant.now();
        if (this.retryCount >= this.maxRetries) {
            this.status = Status.FAILED;
        } else {
            // Exponential backoff: 30s, 2m, 8m, 32m, 128m
            long delaySeconds = (long) Math.pow(4, retryCount) * 30L;
            this.nextRetryAt = Instant.now().plusSeconds(delaySeconds);
        }
    }

    public String getId() { return id; }
    public String getTaskType() { return taskType; }
    public String getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public Status getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public String getLastError() { return lastError; }
}
