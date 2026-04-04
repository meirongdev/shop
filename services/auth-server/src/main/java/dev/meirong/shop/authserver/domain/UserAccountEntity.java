package dev.meirong.shop.authserver.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_account")
public class UserAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "principal_id", nullable = false, unique = true, length = 64)
    private String principalId;

    @Column(nullable = false, unique = true, length = 128)
    private String username;

    @Column(length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "phone_number", length = 32, unique = true)
    private String phoneNumber;

    @Column(nullable = false, length = 32)
    private String portal;

    @Column(nullable = false, length = 255)
    private String roles;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccountEntity() {
    }

    public static UserAccountEntity createForSocialLogin(String email, String displayName, String provider) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.principalId = "player-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        entity.username = provider + "." + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        entity.email = email;
        entity.displayName = displayName;
        entity.portal = "buyer";
        entity.roles = "ROLE_BUYER";
        entity.status = "ACTIVE";
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        return entity;
    }

    public static UserAccountEntity createForBuyerRegistration(String username,
                                                               String email,
                                                               String displayName,
                                                               String passwordHash) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.principalId = "player-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        entity.username = username;
        entity.email = email;
        entity.displayName = displayName;
        entity.passwordHash = passwordHash;
        entity.portal = "buyer";
        entity.roles = "ROLE_BUYER";
        entity.status = "ACTIVE";
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        return entity;
    }

    public static UserAccountEntity createForPhoneLogin(String phoneNumber,
                                                        String username,
                                                        String displayName) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.principalId = "player-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        entity.username = username;
        entity.email = username + "@phone.local";
        entity.displayName = displayName;
        entity.phoneNumber = phoneNumber;
        entity.portal = "buyer";
        entity.roles = "ROLE_BUYER";
        entity.status = "ACTIVE";
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();
        return entity;
    }

    public Long getId() { return id; }
    public String getPrincipalId() { return principalId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getPortal() { return portal; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public List<String> getRoleList() {
        return Arrays.asList(roles.split(","));
    }

    public void setEmail(String email) { this.email = email; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
