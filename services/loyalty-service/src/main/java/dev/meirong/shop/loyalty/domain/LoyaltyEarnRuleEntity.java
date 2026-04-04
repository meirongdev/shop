package dev.meirong.shop.loyalty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "loyalty_earn_rule")
public class LoyaltyEarnRuleEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 32, unique = true)
    private String source;

    @Column(name = "points_formula", nullable = false, length = 32)
    private String pointsFormula;

    @Column(name = "base_value", nullable = false)
    private long baseValue;

    @Column(name = "max_per_day")
    private Long maxPerDay;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LoyaltyEarnRuleEntity() {
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Calculate points for a given dollar amount */
    public long calculate(double amount) {
        return switch (pointsFormula) {
            case "PER_DOLLAR" -> (long) (amount * baseValue);
            case "FIXED" -> baseValue;
            default -> baseValue;
        };
    }

    public String getId() { return id; }
    public String getSource() { return source; }
    public String getPointsFormula() { return pointsFormula; }
    public long getBaseValue() { return baseValue; }
    public Long getMaxPerDay() { return maxPerDay; }
    public boolean isActive() { return active; }
    public Instant getUpdatedAt() { return updatedAt; }
}
