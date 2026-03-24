package dev.meirong.shop.order.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItemEntity, String> {

    List<CartItemEntity> findByBuyerIdOrderByCreatedAtAsc(String buyerId);

    Optional<CartItemEntity> findByBuyerIdAndProductId(String buyerId, String productId);

    void deleteByBuyerId(String buyerId);
}
