package ${package}.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SampleAggregateRepository extends JpaRepository<SampleAggregateEntity, String> {
}
