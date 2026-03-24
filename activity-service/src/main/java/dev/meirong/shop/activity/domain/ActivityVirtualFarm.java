package dev.meirong.shop.activity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "activity_virtual_farm",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_activity_virtual_farm_game_player",
                columnNames = {"game_id", "player_id"}
        )
)
public class ActivityVirtualFarm {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "game_id", nullable = false, length = 36)
    private String gameId;

    @Column(name = "player_id", nullable = false, length = 64)
    private String playerId;

    @Column(nullable = false)
    private int stage;

    @Column(nullable = false)
    private int progress;

    @Column(name = "max_stage", nullable = false)
    private int maxStage;

    @Column(name = "max_progress", nullable = false)
    private int maxProgress;

    @Column(name = "last_water_at")
    private Instant lastWaterAt;

    @Column(name = "harvested_at")
    private Instant harvestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivityVirtualFarm() {}

    public ActivityVirtualFarm(String id, String gameId, String playerId, int maxStage, int maxProgress) {
        if (maxStage <= 0) {
            throw new IllegalArgumentException("maxStage must be greater than zero");
        }
        if (maxProgress <= 0) {
            throw new IllegalArgumentException("maxProgress must be greater than zero");
        }
        this.id = id;
        this.gameId = gameId;
        this.playerId = playerId;
        this.stage = 1;
        this.progress = 0;
        this.maxStage = maxStage;
        this.maxProgress = maxProgress;
        this.createdAt = Instant.now();
    }

    public void water(int gainedProgress) {
        if (gainedProgress <= 0) {
            throw new IllegalArgumentException("gainedProgress must be greater than zero");
        }
        if (isHarvested()) {
            throw new IllegalStateException("Cannot water a harvested farm");
        }
        if (isMatured()) {
            throw new IllegalStateException("Cannot water a matured farm");
        }

        int remaining = gainedProgress;
        while (remaining > 0 && !isMatured()) {
            int needed = maxProgress - progress;
            if (remaining < needed) {
                progress += remaining;
                remaining = 0;
                continue;
            }

            remaining -= needed;
            if (stage == maxStage) {
                progress = maxProgress;
                remaining = 0;
            } else {
                stage++;
                progress = 0;
            }
        }
        lastWaterAt = Instant.now();
    }

    public boolean isMatured() {
        return stage == maxStage && progress >= maxProgress;
    }

    public boolean isHarvested() {
        return harvestedAt != null;
    }

    public void markHarvested() {
        harvestedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getGameId() { return gameId; }
    public String getPlayerId() { return playerId; }
    public int getStage() { return stage; }
    public int getProgress() { return progress; }
    public int getMaxStage() { return maxStage; }
    public int getMaxProgress() { return maxProgress; }
    public Instant getLastWaterAt() { return lastWaterAt; }
    public Instant getHarvestedAt() { return harvestedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
