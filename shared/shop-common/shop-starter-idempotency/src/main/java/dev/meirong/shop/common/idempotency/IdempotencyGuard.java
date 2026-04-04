package dev.meirong.shop.common.idempotency;

import java.util.function.Supplier;

public interface IdempotencyGuard {

    <T> T executeOnce(String key, Supplier<T> action, Supplier<T> fallback);
}
