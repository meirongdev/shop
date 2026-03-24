package dev.meirong.shop.loyalty.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccountEntity, String> {
}
