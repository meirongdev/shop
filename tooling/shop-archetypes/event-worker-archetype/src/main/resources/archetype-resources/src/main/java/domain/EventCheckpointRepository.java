package ${package}.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventCheckpointRepository extends JpaRepository<EventCheckpointEntity, String> {
}
