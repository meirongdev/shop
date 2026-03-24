package dev.meirong.shop.promotion.domain;

import dev.meirong.shop.common.idempotency.IdempotencyRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionIdempotencyKeyRepository
        extends JpaRepository<PromotionIdempotencyKeyEntity, String>, IdempotencyRepository {

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Override
    default boolean existsByKey(String key) {
        return existsByIdempotencyKey(key);
    }
}
