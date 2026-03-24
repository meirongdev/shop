package dev.meirong.shop.activity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityOutboxEventRepository extends JpaRepository<ActivityOutboxEvent, Long> {

    List<ActivityOutboxEvent> findByStatusOrderByCreatedAtAsc(String status);
}
