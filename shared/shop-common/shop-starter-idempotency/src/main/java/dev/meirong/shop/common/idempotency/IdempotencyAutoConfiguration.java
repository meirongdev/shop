package dev.meirong.shop.common.idempotency;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
    @ConditionalOnBean(IdempotencyRepository.class)
    @ConditionalOnProperty(prefix = "shop.idempotency.bloom-filter", name = "enabled", havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean
    IdempotencyGuard dbIdempotencyGuard(IdempotencyRepository idempotencyRepository) {
        return new DbIdempotencyGuard(idempotencyRepository);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
    static class RedisBloomConfiguration {

        @Bean
        @ConditionalOnBean({IdempotencyRepository.class, MeterRegistry.class})
        @ConditionalOnProperty(prefix = "shop.idempotency.bloom-filter", name = "enabled", havingValue = "true")
        @ConditionalOnMissingBean
        IdempotencyGuard redisIdempotencyGuard(org.redisson.api.RedissonClient redissonClient,
                                               BloomFilterProperties properties,
                                               IdempotencyRepository idempotencyRepository,
                                               MeterRegistry meterRegistry,
                                               @Value("${spring.application.name:unknown}") String serviceName) {
            org.redisson.api.RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(properties.requireRedisKey());
            bloomFilter.tryInit(properties.getExpectedInsertions(), properties.getFalseProbability());
            return new RedisIdempotencyGuard(bloomFilter, idempotencyRepository, meterRegistry, serviceName);
        }
    }
}
