package dev.meirong.shop.promotion.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponInstanceRepository extends JpaRepository<CouponInstanceEntity, String> {

    List<CouponInstanceEntity> findByBuyerIdAndStatusOrderByCreatedAtDesc(String buyerId, String status);

    Optional<CouponInstanceEntity> findByBuyerIdAndCode(String buyerId, String code);

    long countByTemplateIdAndBuyerId(String templateId, String buyerId);

    long countByTemplateId(String templateId);

    List<CouponInstanceEntity> findByStatusAndExpiresAtBefore(String status, Instant now);
}
