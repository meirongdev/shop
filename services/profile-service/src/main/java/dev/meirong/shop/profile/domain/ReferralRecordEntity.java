package dev.meirong.shop.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "referral_record")
public class ReferralRecordEntity {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(name = "invite_code", nullable = false, length = 20)
    private String inviteCode;

    @Column(name = "referrer_id", nullable = false, length = 64)
    private String referrerId;

    @Column(name = "invitee_id", nullable = false, length = 64, unique = true)
    private String inviteeId;

    @Column(name = "invitee_username", nullable = false, length = 128)
    private String inviteeUsername;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Column(name = "first_order_at")
    private Instant firstOrderAt;

    @Column(name = "reward_issued_at")
    private Instant rewardIssuedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReferralRecordEntity() {
    }

    public static ReferralRecordEntity registered(String id,
                                                  String inviteCode,
                                                  String referrerId,
                                                  String inviteeId,
                                                  String inviteeUsername) {
        ReferralRecordEntity entity = new ReferralRecordEntity();
        entity.id = id;
        entity.inviteCode = inviteCode;
        entity.referrerId = referrerId;
        entity.inviteeId = inviteeId;
        entity.inviteeUsername = inviteeUsername;
        entity.status = "REGISTERED";
        entity.registeredAt = Instant.now();
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

    public void markRewarded() {
        Instant now = Instant.now();
        this.status = "REWARDED";
        this.firstOrderAt = now;
        this.rewardIssuedAt = now;
    }

    public String getReferrerId() {
        return referrerId;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public String getInviteeUsername() {
        return inviteeUsername;
    }

    public String getStatus() {
        return status;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Instant getRewardIssuedAt() {
        return rewardIssuedAt;
    }
}
