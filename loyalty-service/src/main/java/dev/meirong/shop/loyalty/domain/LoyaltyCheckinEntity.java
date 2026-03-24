package dev.meirong.shop.loyalty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loyalty_checkin")
public class LoyaltyCheckinEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "player_id", nullable = false, length = 64)
    private String buyerId;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @Column(name = "streak_day", nullable = false)
    private int streakDay;

    @Column(name = "points_earned", nullable = false)
    private long pointsEarned;

    @Column(name = "is_makeup", nullable = false)
    private boolean isMakeup;

    @Column(name = "makeup_cost", nullable = false)
    private long makeupCost;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoyaltyCheckinEntity() {
    }

    public LoyaltyCheckinEntity(String buyerId, LocalDate checkinDate, int streakDay,
                                long pointsEarned, boolean isMakeup, long makeupCost) {
        this.id = UUID.randomUUID().toString();
        this.buyerId = buyerId;
        this.checkinDate = checkinDate;
        this.streakDay = streakDay;
        this.pointsEarned = pointsEarned;
        this.isMakeup = isMakeup;
        this.makeupCost = makeupCost;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getBuyerId() { return buyerId; }
    public LocalDate getCheckinDate() { return checkinDate; }
    public int getStreakDay() { return streakDay; }
    public long getPointsEarned() { return pointsEarned; }
    public boolean isMakeup() { return isMakeup; }
    public long getMakeupCost() { return makeupCost; }
    public Instant getCreatedAt() { return createdAt; }
}
