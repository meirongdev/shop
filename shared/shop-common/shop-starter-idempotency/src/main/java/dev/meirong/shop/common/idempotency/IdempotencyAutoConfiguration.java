package dev.meirong.shop.common.idempotency;

import dev.meirong.shop.common.idempotency.BloomFilterProperties;
import dev.meirong.shop.common.idempotency.DbIdempotencyGuard;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import dev.meirong.shop.common.idempotency.IdempotencyRepository;
import dev.meirong.shop.common.idempotency.RedisIdempotencyGuard;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfiguration(after = JpaRepositoriesAutoConfiguration.class)
@EnableConfigurationProperties(BloomFilterProperties.class)
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "shop.idempotency.bloom-filter", name = "enabled", havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(IdempotencyGuard.class)
    IdempotencyGuard dbIdempotencyGuard(ObjectProvider<IdempotencyRepository> idempotencyRepositoryProvider) {
        IdempotencyRepository repository = idempotencyRepositoryProvider.getIfAvailable();
        if (repository == null) {
            // Not all services require idempotency — skip bean creation gracefully.
            // Services that DO need it must provide an IdempotencyRepository implementation.
            return null;
        }
        return new DbIdempotencyGuard(repository);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
    static class RedisBloomConfiguration {

        @Bean
        @ConditionalOnProperty(prefix = "shop.idempotency.bloom-filter", name = "enabled", havingValue = "true")
        @ConditionalOnMissingBean(IdempotencyGuard.class)
        IdempotencyGuard redisIdempotencyGuard(ObjectProvider<IdempotencyRepository> idempotencyRepositoryProvider,
                                               ObjectProvider<MeterRegistry> meterRegistryProvider,
                                               org.redisson.api.RedissonClient redissonClient,
                                               BloomFilterProperties properties,
                                               @Value("${spring.application.name:unknown}") String serviceName) {
            IdempotencyRepository repository = idempotencyRepositoryProvider.getIfAvailable();
            MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
            if (repository == null || meterRegistry == null) {
                // Graceful skip — not all services require idempotency.
                return null;
            }

            org.redisson.api.RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(properties.requireRedisKey());
            bloomFilter.tryInit(properties.getExpectedInsertions(), properties.getFalseProbability());
            return new RedisIdempotencyGuard(bloomFilter, repository, meterRegistry, serviceName);
        }
    }
}
