package dev.meirong.shop.order.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopOrderRepository extends JpaRepository<ShopOrderEntity, String> {

    List<ShopOrderEntity> findByBuyerIdOrderByCreatedAtDesc(String buyerId);

    List<ShopOrderEntity> findBySellerIdOrderByCreatedAtDesc(String sellerId);

    List<ShopOrderEntity> findByStatusAndCreatedAtBefore(String status, Instant threshold);

    List<ShopOrderEntity> findByStatusAndDeliveredAtBefore(String status, Instant threshold);

    Optional<ShopOrderEntity> findByPaymentTransactionId(String paymentTransactionId);

    Optional<ShopOrderEntity> findByOrderToken(String orderToken);
}
