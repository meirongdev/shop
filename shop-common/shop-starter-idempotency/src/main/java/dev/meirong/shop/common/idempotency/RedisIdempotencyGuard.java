package dev.meirong.shop.common.idempotency;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.function.Supplier;
import org.redisson.api.RBloomFilter;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

public class RedisIdempotencyGuard implements IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyGuard.class);

    private final RBloomFilter<String> bloomFilter;
    private final IdempotencyRepository repository;
    private final Counter duplicateHitCounter;
    private final Counter falsePositiveHitCounter;
    private final Counter missCounter;
    private final Counter fallbackCounter;

    public RedisIdempotencyGuard(RBloomFilter<String> bloomFilter,
                                 IdempotencyRepository repository,
                                 MeterRegistry meterRegistry,
                                 String serviceName) {
        this.bloomFilter = Objects.requireNonNull(bloomFilter, "bloomFilter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        String resolvedServiceName = serviceName == null || serviceName.isBlank() ? "unknown" : serviceName;
        this.duplicateHitCounter = meterRegistry.counter("shop.idempotency.bf.hit",
                "service", resolvedServiceName, "result", "duplicate");
        this.falsePositiveHitCounter = meterRegistry.counter("shop.idempotency.bf.hit",
                "service", resolvedServiceName, "result", "false_positive");
        this.missCounter = meterRegistry.counter("shop.idempotency.bf.miss", "service", resolvedServiceName);
        this.fallbackCounter = meterRegistry.counter("shop.idempotency.bf.fallback", "service", resolvedServiceName);
    }

    @Override
    public <T> T executeOnce(String key, Supplier<T> action, Supplier<T> fallback) {
        validateKey(key);
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(fallback, "fallback must not be null");

        try {
            if (!bloomFilter.contains(key)) {
                missCounter.increment();
                return executeAction(key, action, fallback);
            }
        } catch (RedisException exception) {
            log.warn("Bloom filter unavailable for key {}, degrading to DB path: {}", key, exception.getMessage());
            fallbackCounter.increment();
            return executeViaDb(key, action, fallback);
        }

        if (repository.existsByKey(key)) {
            duplicateHitCounter.increment();
            return fallback.get();
        }

        falsePositiveHitCounter.increment();
        return executeAction(key, action, fallback);
    }

    private <T> T executeViaDb(String key, Supplier<T> action, Supplier<T> fallback) {
        if (repository.existsByKey(key)) {
            duplicateHitCounter.increment();
            return fallback.get();
        }
        return executeAction(key, action, fallback);
    }

    private <T> T executeAction(String key, Supplier<T> action, Supplier<T> fallback) {
        try {
            T result = action.get();
            addQuietly(key);
            return result;
        } catch (DataIntegrityViolationException exception) {
            log.debug("Concurrent duplicate write detected for key {}, replaying fallback", key);
            addQuietly(key);
            return fallback.get();
        }
    }

    private void addQuietly(String key) {
        try {
            bloomFilter.add(key);
        } catch (RedisException exception) {
            log.warn("Failed to add key {} to bloom filter, DB remains source of truth: {}",
                    key, exception.getMessage());
            fallbackCounter.increment();
        } catch (RuntimeException exception) {
            log.warn("Unexpected bloom filter add failure for key {}: {}", key, exception.getMessage());
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
