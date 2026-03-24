package ${package}.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_checkpoint")
public class EventCheckpointEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(name = "event_key", nullable = false, length = 128)
    private String eventKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EventCheckpointEntity() {
    }

    public EventCheckpointEntity(String eventKey, String payload) {
        this.id = UUID.randomUUID().toString();
        this.eventKey = eventKey;
        this.payload = payload;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
