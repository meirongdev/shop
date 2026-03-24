package dev.meirong.shop.promotion.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<CouponEntity, String> {

    Optional<CouponEntity> findByCode(String code);

    List<CouponEntity> findBySellerIdOrderByCreatedAtDesc(String sellerId);

    List<CouponEntity> findByActiveTrueOrderByCreatedAtDesc();
}
