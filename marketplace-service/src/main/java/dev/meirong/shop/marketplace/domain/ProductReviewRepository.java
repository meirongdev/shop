package dev.meirong.shop.marketplace.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductReviewRepository extends JpaRepository<ProductReviewEntity, String> {

    Page<ProductReviewEntity> findByProductIdAndStatusOrderByCreatedAtDesc(
            String productId, String status, Pageable pageable);

    List<ProductReviewEntity> findByProductIdAndBuyerIdAndOrderId(
            String productId, String buyerId, String orderId);

    @Query("SELECT AVG(r.rating) FROM ProductReviewEntity r WHERE r.productId = :productId AND r.status = 'APPROVED'")
    Double findAverageRatingByProductId(String productId);

    @Query("SELECT COUNT(r) FROM ProductReviewEntity r WHERE r.productId = :productId AND r.status = 'APPROVED'")
    long countApprovedByProductId(String productId);
}
