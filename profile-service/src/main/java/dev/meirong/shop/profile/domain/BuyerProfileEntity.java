package dev.meirong.shop.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "buyer_profile")
public class BuyerProfileEntity {

    @Id
    @Column(name = "buyer_id", nullable = false, length = 64)
    private String buyerId;

    @Column(nullable = false, unique = true, length = 128)
    private String username;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(nullable = false, length = 256)
    private String email;

    @Column(nullable = false, length = 32)
    private String tier;

    @Column(name = "invite_code", nullable = false, unique = true, length = 20)
    private String inviteCode;

    @Column(name = "invite_code_expire_at", nullable = false)
    private Instant inviteCodeExpireAt;

    @Column(name = "referrer_id", length = 64)
    private String referrerId;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BuyerProfileEntity() {
    }

    public BuyerProfileEntity(String buyerId, String username, String displayName, String email, String tier) {
        this.buyerId = buyerId;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.tier = tier;
    }

    public static BuyerProfileEntity register(String buyerId,
                                              String username,
                                              String displayName,
                                              String email,
                                              String tier,
                                              String inviteCode,
                                              Instant inviteCodeExpireAt,
                                              String referrerId) {
        BuyerProfileEntity entity = new BuyerProfileEntity(buyerId, username, displayName, email, tier);
        entity.inviteCode = inviteCode;
        entity.inviteCodeExpireAt = inviteCodeExpireAt;
        entity.referrerId = referrerId;
        entity.emailVerified = false;
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

    public void update(String displayName, String email, String tier) {
        this.displayName = displayName;
        this.email = email;
        this.tier = tier;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getTier() {
        return tier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public Instant getInviteCodeExpireAt() {
        return inviteCodeExpireAt;
    }

    public String getReferrerId() {
        return referrerId;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }
}
