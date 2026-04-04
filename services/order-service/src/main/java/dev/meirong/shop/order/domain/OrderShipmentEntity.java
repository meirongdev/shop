package dev.meirong.shop.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_shipment")
public class OrderShipmentEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "order_id", nullable = false, length = 36, unique = true)
    private String orderId;

    @Column(length = 64)
    private String carrier;

    @Column(name = "tracking_no", length = 128)
    private String trackingNo;

    @Column(name = "tracking_url", length = 512)
    private String trackingUrl;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderShipmentEntity() {
    }

    public OrderShipmentEntity(String orderId, String carrier, String trackingNo) {
        this.id = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.carrier = carrier;
        this.trackingNo = trackingNo;
        this.status = "SHIPPED";
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

    public void markDelivered() {
        this.status = "DELIVERED";
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getCarrier() { return carrier; }
    public String getTrackingNo() { return trackingNo; }
    public String getTrackingUrl() { return trackingUrl; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
