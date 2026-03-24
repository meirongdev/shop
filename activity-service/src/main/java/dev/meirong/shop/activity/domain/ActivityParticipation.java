package dev.meirong.shop.activity.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "activity_participation")
public class ActivityParticipation {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "game_id", nullable = false, length = 36)
    private String gameId;

    @Column(name = "game_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private GameType gameType;

    @Column(name = "player_id", length = 64)
    private String playerId;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "device_fingerprint", length = 256)
    private String deviceFingerprint;

    @Column(name = "participated_at", nullable = false)
    private Instant participatedAt;

    @Column(nullable = false, length = 20)
    private String result = "PENDING";

    @Column(name = "prize_id", length = 36)
    private String prizeId;

    @Column(name = "prize_snapshot", columnDefinition = "JSON")
    private String prizeSnapshot;

    @Column(name = "reward_status", nullable = false, length = 20)
    private String rewardStatus = "PENDING";

    @Column(name = "reward_ref", length = 128)
    private String rewardRef;

    @Column(name = "extra_data", columnDefinition = "JSON")
    private String extraData;

    protected ActivityParticipation() {}

    public ActivityParticipation(String id, String gameId, GameType gameType, String playerId) {
        this.id = id;
        this.gameId = gameId;
        this.gameType = gameType;
        this.playerId = playerId;
        this.participatedAt = Instant.now();
    }

    public void markWin(String prizeId, String prizeSnapshot) {
        this.result = "WIN";
        this.prizeId = prizeId;
        this.prizeSnapshot = prizeSnapshot;
    }

    public void markMiss() {
        this.result = "MISS";
        this.rewardStatus = "SKIPPED";
    }

    public void markDispatched(String rewardRef) {
        this.rewardStatus = "DISPATCHED";
        this.rewardRef = rewardRef;
    }

    public void markRewardSkipped() {
        this.rewardStatus = "SKIPPED";
    }

    public void markFailed() {
        this.rewardStatus = "FAILED";
    }

    // Getters and setters
    public String getId() { return id; }
    public String getGameId() { return gameId; }
    public GameType getGameType() { return gameType; }
    public String getPlayerId() { return playerId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
    public Instant getParticipatedAt() { return participatedAt; }
    public String getResult() { return result; }
    public String getPrizeId() { return prizeId; }
    public String getPrizeSnapshot() { return prizeSnapshot; }
    public String getRewardStatus() { return rewardStatus; }
    public void setRewardStatus(String rewardStatus) { this.rewardStatus = rewardStatus; }
    public String getRewardRef() { return rewardRef; }
    public String getExtraData() { return extraData; }
    public void setExtraData(String extraData) { this.extraData = extraData; }
}
