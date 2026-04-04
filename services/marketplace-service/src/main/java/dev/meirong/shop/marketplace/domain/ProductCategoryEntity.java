package dev.meirong.shop.marketplace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_category")
public class ProductCategoryEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(nullable = false, length = 256)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProductCategoryEntity() {
    }

    public ProductCategoryEntity(String name, String description) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
