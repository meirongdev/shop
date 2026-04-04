package dev.meirong.shop.marketplace.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategoryEntity, String> {

    List<ProductCategoryEntity> findAllByOrderByNameAsc();
}
