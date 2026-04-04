package dev.meirong.shop.promotion.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponTemplateRepository extends JpaRepository<CouponTemplateEntity, String> {

    Optional<CouponTemplateEntity> findByCode(String code);

    List<CouponTemplateEntity> findByActiveTrueOrderByCreatedAtDesc();

    List<CouponTemplateEntity> findBySellerIdAndActiveTrueOrderByCreatedAtDesc(String sellerId);
}
