package dev.meirong.shop.common.idempotency;

import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;

public class DbIdempotencyGuard implements IdempotencyGuard {

    private final IdempotencyRepository repository;

    public DbIdempotencyGuard(IdempotencyRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public <T> T executeOnce(String key, Supplier<T> action, Supplier<T> fallback) {
        validateKey(key);
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(fallback, "fallback must not be null");

        if (repository.existsByKey(key)) {
            return fallback.get();
        }

        try {
            return action.get();
        } catch (DataIntegrityViolationException exception) {
            return fallback.get();
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
        if (key.length() > 128) {
            throw new IllegalArgumentException("idempotency key length must be <= 128");
        }
    }
}
