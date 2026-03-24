package dev.meirong.shop.loyalty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "onboarding_task_progress")
public class OnboardingTaskProgressEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "player_id", nullable = false, length = 64)
    private String playerId;

    @Column(name = "task_key", nullable = false, length = 64)
    private String taskKey;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "points_issued", nullable = false)
    private long pointsIssued;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expire_at", nullable = false)
    private Instant expireAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OnboardingTaskProgressEntity() {
    }

    public static OnboardingTaskProgressEntity init(String playerId, String taskKey, Instant expireAt) {
        OnboardingTaskProgressEntity entity = new OnboardingTaskProgressEntity();
        entity.id = UUID.randomUUID().toString();
        entity.playerId = playerId;
        entity.taskKey = taskKey;
        entity.status = "PENDING";
        entity.pointsIssued = 0;
        entity.expireAt = expireAt;
        return entity;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(this.status);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(this.expireAt);
    }

    public void complete(long points) {
        this.status = "COMPLETED";
        this.pointsIssued = points;
        this.completedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getPlayerId() { return playerId; }
    public String getTaskKey() { return taskKey; }
    public String getStatus() { return status; }
    public long getPointsIssued() { return pointsIssued; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getExpireAt() { return expireAt; }
    public Instant getCreatedAt() { return createdAt; }
}
