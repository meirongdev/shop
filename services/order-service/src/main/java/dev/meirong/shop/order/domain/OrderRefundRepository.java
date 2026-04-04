package dev.meirong.shop.order.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRefundRepository extends JpaRepository<OrderRefundEntity, String> {

    List<OrderRefundEntity> findByOrderId(String orderId);
}
