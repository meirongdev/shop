package dev.meirong.shop.authserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "social_account")
public class SocialAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_account_id", nullable = false)
    private Long userAccountId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(length = 255)
    private String email;

    @Column(length = 128)
    private String name;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SocialAccountEntity() {
    }

    public static SocialAccountEntity create(Long userAccountId, String provider,
                                             String providerUserId, String email,
                                             String name, String avatarUrl) {
        SocialAccountEntity entity = new SocialAccountEntity();
        entity.userAccountId = userAccountId;
        entity.provider = provider;
        entity.providerUserId = providerUserId;
        entity.email = email;
        entity.name = name;
        entity.avatarUrl = avatarUrl;
        entity.createdAt = Instant.now();
        return entity;
    }

    public Long getId() { return id; }
    public Long getUserAccountId() { return userAccountId; }
    public String getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getAvatarUrl() { return avatarUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
