package dev.meirong.shop.marketplace.domain;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceProductRepository extends JpaRepository<MarketplaceProductEntity, String> {

    List<MarketplaceProductEntity> findByPublishedTrueOrderByNameAsc();

    long countBySellerId(String sellerId);

    List<MarketplaceProductEntity> findBySellerIdOrderByNameAsc(String sellerId);

    @Query("SELECT p FROM MarketplaceProductEntity p WHERE p.published = true " +
           "AND (:query IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:categoryId IS NULL OR p.categoryId = :categoryId) " +
           "ORDER BY p.name ASC")
    Page<MarketplaceProductEntity> searchProducts(@Param("query") String query,
                                                   @Param("categoryId") String categoryId,
                                                   Pageable pageable);
}
