package dev.meirong.shop.marketplace.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, String> {

    List<ProductVariantEntity> findByProductIdOrderByDisplayOrderAsc(String productId);
}
