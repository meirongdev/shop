package ${package}.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sample_aggregate")
public class SampleAggregateEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SampleAggregateEntity() {
    }

    public SampleAggregateEntity(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
