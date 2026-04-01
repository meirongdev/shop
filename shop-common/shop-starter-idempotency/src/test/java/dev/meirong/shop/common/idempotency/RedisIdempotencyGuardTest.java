package dev.meirong.shop.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;
import org.redisson.client.RedisException;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class RedisIdempotencyGuardTest {

    @Mock
    private RBloomFilter<String> bloomFilter;

    @Mock
    private IdempotencyRepository repository;

    private MeterRegistry meterRegistry;
    private RedisIdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        guard = new RedisIdempotencyGuard(bloomFilter, repository, meterRegistry, "test-service");
    }

    @Test
    void bfMiss_executesAction_addsToBloomFilter() {
        when(bloomFilter.contains("key-1")).thenReturn(false);

        String result = guard.executeOnce("key-1", () -> "done", () -> "fallback");

        assertThat(result).isEqualTo("done");
        verify(bloomFilter).add("key-1");
        verify(repository, never()).existsByKey(any());
    }

    @Test
    void bfHit_dbConfirms_returnsFallback() {
        when(bloomFilter.contains("key-2")).thenReturn(true);
        when(repository.existsByKey("key-2")).thenReturn(true);

        String result = guard.executeOnce("key-2", () -> "action", () -> "cached");

        assertThat(result).isEqualTo("cached");
        verify(bloomFilter, never()).add(any(String.class));
    }

    @Test
    void bfHit_dbDenies_falsePositive_executesAction() {
        when(bloomFilter.contains("key-3")).thenReturn(true);
        when(repository.existsByKey("key-3")).thenReturn(false);

        String result = guard.executeOnce("key-3", () -> "executed", () -> "fallback");

        assertThat(result).isEqualTo("executed");
        verify(bloomFilter).add("key-3");
    }

    @Test
    void actionThrowsDataIntegrityViolation_returnsFallback() {
        when(bloomFilter.contains("key-4")).thenReturn(false);

        String result = guard.executeOnce("key-4",
                () -> {
                    throw new DataIntegrityViolationException("dup");
                },
                () -> "concurrent-result");

        assertThat(result).isEqualTo("concurrent-result");
        verify(bloomFilter).add("key-4");
    }

    @Test
    void actionThrowsBusinessException_doesNotWriteBloomFilter() {
        when(bloomFilter.contains("key-5")).thenReturn(false);

        assertThatThrownBy(() -> guard.executeOnce("key-5",
                () -> {
                    throw new IllegalStateException("business error");
                },
                () -> "fallback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("business error");

        verify(bloomFilter, never()).add(any(String.class));
    }

    @Test
    void redisUnavailable_degradesToDbPath_actionRuns() {
        when(bloomFilter.contains("key-6")).thenThrow(new RedisException("Redis down"));
        when(repository.existsByKey("key-6")).thenReturn(false);

        String result = guard.executeOnce("key-6", () -> "db-path-result", () -> "fallback");

        assertThat(result).isEqualTo("db-path-result");
        assertThat(meterRegistry.counter("shop.idempotency.bf.fallback", "service", "test-service").count())
                .isEqualTo(1.0);
    }

    @Test
    void redisUnavailable_dbConfirms_returnsFallback() {
        when(bloomFilter.contains("key-7")).thenThrow(new RedisException("Redis down"));
        when(repository.existsByKey("key-7")).thenReturn(true);

        String result = guard.executeOnce("key-7", () -> "action", () -> "cached");

        assertThat(result).isEqualTo("cached");
    }

    @Test
    void bfAddFailsAfterAction_doesNotPropagateException() {
        when(bloomFilter.contains("key-8")).thenReturn(false);
        doThrow(new RedisException("Redis flap")).when(bloomFilter).add(eq("key-8"));

        String result = guard.executeOnce("key-8", () -> "ok", () -> "fallback");

        assertThat(result).isEqualTo("ok");
        assertThat(meterRegistry.counter("shop.idempotency.bf.fallback", "service", "test-service").count())
                .isEqualTo(1.0);
    }

    @Test
    void fallbackThrows_exceptionPropagates() {
        when(bloomFilter.contains("key-9")).thenReturn(true);
        when(repository.existsByKey("key-9")).thenReturn(true);

        assertThatThrownBy(() -> guard.executeOnce("key-9",
                () -> "action",
                () -> {
                    throw new IllegalStateException("no result");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("no result");
    }

    @Test
    void nullResultFromAction_isValid() {
        when(bloomFilter.contains("key-10")).thenReturn(false);

        String result = guard.executeOnce("key-10", () -> null, () -> "fallback");

        assertThat(result).isNull();
        verify(bloomFilter).add("key-10");
    }
}
