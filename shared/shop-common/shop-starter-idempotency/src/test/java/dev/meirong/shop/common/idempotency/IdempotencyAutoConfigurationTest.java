package dev.meirong.shop.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class IdempotencyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdempotencyAutoConfiguration.class));

    @Test
    void createsDbGuardWhenRepositoryPresent() {
        contextRunner
                .withUserConfiguration(DbGuardConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyGuard.class);
                    assertThat(context.getBean(IdempotencyGuard.class)).isInstanceOf(DbIdempotencyGuard.class);
                });
    }

    @Test
    void createsRedisGuardWhenBloomFilterEnabled() {
        contextRunner
                .withPropertyValues(
                        "shop.idempotency.bloom-filter.enabled=true",
                        "shop.idempotency.bloom-filter.redis-key=shop:test:idempotency")
                .withUserConfiguration(RedisGuardConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdempotencyGuard.class);
                    assertThat(context.getBean(IdempotencyGuard.class)).isInstanceOf(RedisIdempotencyGuard.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class DbGuardConfiguration {

        @Bean
        IdempotencyRepository idempotencyRepository() {
            return key -> false;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisGuardConfiguration {

        @Bean
        IdempotencyRepository idempotencyRepository() {
            return key -> false;
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        RedissonClient redissonClient() {
            RedissonClient redissonClient = mock(RedissonClient.class);
            @SuppressWarnings("unchecked")
            RBloomFilter<Object> bloomFilter = mock(RBloomFilter.class);
            when(redissonClient.getBloomFilter("shop:test:idempotency")).thenReturn(bloomFilter);
            when(bloomFilter.tryInit(1_000_000L, 0.001d)).thenReturn(true);
            return redissonClient;
        }
    }
}
