package dev.meirong.shop.loyalty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "onboarding_task_template")
public class OnboardingTaskTemplateEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "task_key", nullable = false, length = 64, unique = true)
    private String taskKey;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 256)
    private String description;

    @Column(name = "points_reward", nullable = false)
    private long pointsReward;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    protected OnboardingTaskTemplateEntity() {
    }

    public String getId() { return id; }
    public String getTaskKey() { return taskKey; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public long getPointsReward() { return pointsReward; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
}
