package dev.meirong.shop.wallet.config;

import dev.meirong.shop.common.idempotency.BloomFilterProperties;
import dev.meirong.shop.common.idempotency.DbIdempotencyGuard;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import dev.meirong.shop.common.idempotency.RedisIdempotencyGuard;
import dev.meirong.shop.wallet.domain.WalletIdempotencyKeyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BloomFilterProperties.class)
public class WalletIdempotencyConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyGuard.class)
    @ConditionalOnProperty(prefix = "shop.idempotency.bloom-filter", name = "enabled", havingValue = "false", matchIfMissing = true)
    IdempotencyGuard walletDbIdempotencyGuard(WalletIdempotencyKeyRepository idempotencyRepository) {
        return new DbIdempotencyGuard(idempotencyRepository);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotencyGuard.class)
    @ConditionalOnProperty(prefix = "shop.idempotency.bloom-filter", name = "enabled", havingValue = "true")
    IdempotencyGuard walletRedisIdempotencyGuard(RedissonClient redissonClient,
                                                 BloomFilterProperties properties,
                                                 WalletIdempotencyKeyRepository idempotencyRepository,
                                                 MeterRegistry meterRegistry,
                                                 @Value("${spring.application.name:unknown}") String serviceName) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(properties.requireRedisKey());
        bloomFilter.tryInit(properties.getExpectedInsertions(), properties.getFalseProbability());
        return new RedisIdempotencyGuard(bloomFilter, idempotencyRepository, meterRegistry, serviceName);
    }
}
