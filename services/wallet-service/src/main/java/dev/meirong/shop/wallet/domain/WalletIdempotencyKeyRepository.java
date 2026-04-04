package dev.meirong.shop.wallet.domain;

import dev.meirong.shop.common.idempotency.IdempotencyRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletIdempotencyKeyRepository
        extends JpaRepository<WalletIdempotencyKeyEntity, String>, IdempotencyRepository {

    Optional<WalletIdempotencyKeyEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Override
    default boolean existsByKey(String key) {
        return existsByIdempotencyKey(key);
    }
}
