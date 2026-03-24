package dev.meirong.shop.order.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderOutboxEventRepository extends JpaRepository<OrderOutboxEventEntity, String> {

    List<OrderOutboxEventEntity> findTop20ByPublishedFalseOrderByCreatedAtAsc();
}
