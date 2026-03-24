package dev.meirong.shop.order.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderShipmentRepository extends JpaRepository<OrderShipmentEntity, String> {

    Optional<OrderShipmentEntity> findByOrderId(String orderId);
}
