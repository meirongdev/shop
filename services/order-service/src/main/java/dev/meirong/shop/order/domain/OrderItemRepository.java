package dev.meirong.shop.order.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItemEntity, String> {

    List<OrderItemEntity> findByOrderId(String orderId);

    List<OrderItemEntity> findByOrderIdIn(List<String> orderIds);
}
