package dev.meirong.shop.marketplace.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_variant")
public class ProductVariantEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "product_id", nullable = false, length = 36)
    private String productId;

    @Column(name = "variant_name", nullable = false, length = 128)
    private String variantName;

    @Column(nullable = false, columnDefinition = "JSON")
    private String attributes;

    @Column(name = "price_adjust", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceAdjust = BigDecimal.ZERO;

    @Column(nullable = false)
    private int inventory = 0;

    @Column(name = "sku_suffix", length = 32)
    private String skuSuffix;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductVariantEntity() {}

    public ProductVariantEntity(String productId, String variantName, String attributes,
                                 BigDecimal priceAdjust, int inventory, String skuSuffix) {
        this.id = UUID.randomUUID().toString();
        this.productId = productId;
        this.variantName = variantName;
        this.attributes = attributes;
        this.priceAdjust = priceAdjust;
        this.inventory = inventory;
        this.skuSuffix = skuSuffix;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public boolean deductInventory(int quantity) {
        if (this.inventory < quantity) return false;
        this.inventory -= quantity;
        return true;
    }

    public void restoreInventory(int quantity) {
        this.inventory += quantity;
    }

    // Getters and setters
    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getVariantName() { return variantName; }
    public String getAttributes() { return attributes; }
    public BigDecimal getPriceAdjust() { return priceAdjust; }
    public int getInventory() { return inventory; }
    public String getSkuSuffix() { return skuSuffix; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public void setVariantName(String variantName) { this.variantName = variantName; }
    public void setAttributes(String attributes) { this.attributes = attributes; }
    public void setPriceAdjust(BigDecimal priceAdjust) { this.priceAdjust = priceAdjust; }
    public void setInventory(int inventory) { this.inventory = inventory; }
    public void setSkuSuffix(String skuSuffix) { this.skuSuffix = skuSuffix; }
}
