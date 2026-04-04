package dev.meirong.shop.loyalty.domain;

import dev.meirong.shop.common.idempotency.IdempotencyRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyIdempotencyKeyRepository
        extends JpaRepository<LoyaltyIdempotencyKeyEntity, String>, IdempotencyRepository {

    @Override
    default boolean existsByKey(String key) {
        return existsById(key);
    }
}
