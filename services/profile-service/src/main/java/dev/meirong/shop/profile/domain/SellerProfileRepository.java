package dev.meirong.shop.profile.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepository extends JpaRepository<SellerProfileEntity, String> {

    Optional<SellerProfileEntity> findByShopSlug(String shopSlug);
}
