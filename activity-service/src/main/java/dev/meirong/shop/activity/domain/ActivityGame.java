package dev.meirong.shop.activity.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "activity_game")
public class ActivityGame {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private GameType type;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private GameStatus status;

    @Column(columnDefinition = "JSON")
    private String config;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "per_user_daily_limit", nullable = false)
    private int perUserDailyLimit = 1;

    @Column(name = "per_user_total_limit", nullable = false)
    private int perUserTotalLimit = 3;

    @Column(name = "entry_condition", columnDefinition = "JSON")
    private String entryCondition;

    @Column(name = "participant_count", nullable = false)
    private int participantCount = 0;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActivityGame() {}

    public ActivityGame(String id, GameType type, String name) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.status = GameStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        if (status != GameStatus.ACTIVE) return false;
        Instant now = Instant.now();
        if (startAt != null && now.isBefore(startAt)) return false;
        if (endAt != null && now.isAfter(endAt)) return false;
        return true;
    }

    public void activate() {
        if (status != GameStatus.DRAFT && status != GameStatus.SCHEDULED) {
            throw new IllegalStateException("Cannot activate game in status: " + status);
        }
        this.status = GameStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void end() {
        if (status != GameStatus.ACTIVE) {
            throw new IllegalStateException("Cannot end game in status: " + status);
        }
        this.status = GameStatus.ENDED;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (status == GameStatus.ENDED || status == GameStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel game in status: " + status);
        }
        this.status = GameStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public void incrementParticipantCount() {
        this.participantCount++;
        this.updatedAt = Instant.now();
    }

    // Getters and setters
    public String getId() { return id; }
    public GameType getType() { return type; }
    public String getName() { return name; }
    public GameStatus getStatus() { return status; }
    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public int getPerUserDailyLimit() { return perUserDailyLimit; }
    public void setPerUserDailyLimit(int perUserDailyLimit) { this.perUserDailyLimit = perUserDailyLimit; }
    public int getPerUserTotalLimit() { return perUserTotalLimit; }
    public void setPerUserTotalLimit(int perUserTotalLimit) { this.perUserTotalLimit = perUserTotalLimit; }
    public String getEntryCondition() { return entryCondition; }
    public void setEntryCondition(String entryCondition) { this.entryCondition = entryCondition; }
    public int getParticipantCount() { return participantCount; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setName(String name) { this.name = name; }
    public void setStatus(GameStatus status) { this.status = status; }
}
