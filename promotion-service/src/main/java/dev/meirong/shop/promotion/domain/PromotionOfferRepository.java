package dev.meirong.shop.promotion.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionOfferRepository extends JpaRepository<PromotionOfferEntity, String> {

    List<PromotionOfferEntity> findByActiveTrueOrderByCreatedAtDesc();

    List<PromotionOfferEntity> findBySourceOrderByCreatedAtDesc(String source);

    boolean existsByCode(String code);

    long countBySourceAndActiveTrue(String source);

    List<PromotionOfferEntity> findByActiveTrue();

    java.util.Optional<PromotionOfferEntity> findByCode(String code);
}
