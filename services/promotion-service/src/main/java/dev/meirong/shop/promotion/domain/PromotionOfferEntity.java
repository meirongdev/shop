package dev.meirong.shop.promotion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promotion_offer")
public class PromotionOfferEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, length = 512)
    private String description;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(columnDefinition = "JSON")
    private String conditions;

    @Column(columnDefinition = "JSON")
    private String benefits;

    @Column(name = "stacking_policy", nullable = false, length = 16)
    private String stackingPolicy;

    @Column(nullable = false)
    private int priority;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "reward_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal rewardAmount;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PromotionOfferEntity() {
    }

    public PromotionOfferEntity(String code,
                                String title,
                                String description,
                                BigDecimal rewardAmount,
                                boolean active,
                                String source) {
        this.id = UUID.randomUUID().toString();
        this.code = code;
        this.title = title;
        this.description = description;
        this.type = "SIMPLE";
        this.stackingPolicy = "EXCLUSIVE";
        this.priority = 0;
        this.rewardAmount = rewardAmount;
        this.active = active;
        this.source = source;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public String getConditions() { return conditions; }
    public String getBenefits() { return benefits; }
    public String getStackingPolicy() { return stackingPolicy; }
    public int getPriority() { return priority; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public BigDecimal getRewardAmount() { return rewardAmount; }
    public boolean isActive() { return active; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }

    public void setType(String type) { this.type = type; }
    public void setConditions(String conditions) { this.conditions = conditions; }
    public void setBenefits(String benefits) { this.benefits = benefits; }
    public void setStackingPolicy(String stackingPolicy) { this.stackingPolicy = stackingPolicy; }
    public void setPriority(int priority) { this.priority = priority; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }
    public void deactivate() { this.active = false; }
}
