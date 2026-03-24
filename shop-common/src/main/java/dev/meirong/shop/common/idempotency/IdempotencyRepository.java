package dev.meirong.shop.common.idempotency;

public interface IdempotencyRepository {

    boolean existsByKey(String key);
}
