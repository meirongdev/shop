package dev.meirong.shop.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "seller_profile")
public class SellerProfileEntity {

    @Id
    @Column(name = "seller_id", nullable = false, length = 64)
    private String sellerId;

    @Column(nullable = false, unique = true, length = 128)
    private String username;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "shop_name", length = 128)
    private String shopName;

    @Column(name = "shop_slug", length = 64, unique = true)
    private String shopSlug;

    @Column(name = "shop_description", columnDefinition = "TEXT")
    private String shopDescription;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Column(name = "banner_url", length = 512)
    private String bannerUrl;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Column(name = "total_sales")
    private int totalSales;

    @Column(nullable = false, length = 256)
    private String email;

    @Column(nullable = false, length = 32)
    private String tier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SellerProfileEntity() {
    }

    public SellerProfileEntity(String sellerId, String username, String displayName, String email, String tier) {
        this.sellerId = sellerId;
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.tier = tier;
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

    public void updateShop(String shopName, String shopSlug, String shopDescription, String logoUrl, String bannerUrl) {
        this.shopName = shopName;
        this.shopSlug = shopSlug;
        this.shopDescription = shopDescription;
        this.logoUrl = logoUrl;
        this.bannerUrl = bannerUrl;
    }

    public void incrementSales(int count) {
        this.totalSales += count;
    }

    public void updateAvgRating(double rating) {
        this.avgRating = BigDecimal.valueOf(rating).setScale(2, RoundingMode.HALF_UP);
    }

    public String getSellerId() { return sellerId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getShopName() { return shopName; }
    public String getShopSlug() { return shopSlug; }
    public String getShopDescription() { return shopDescription; }
    public String getLogoUrl() { return logoUrl; }
    public String getBannerUrl() { return bannerUrl; }
    public double getAvgRating() { return avgRating.doubleValue(); }
    public int getTotalSales() { return totalSales; }
    public String getEmail() { return email; }
    public String getTier() { return tier; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
