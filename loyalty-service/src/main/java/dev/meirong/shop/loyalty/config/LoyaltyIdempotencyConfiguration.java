package dev.meirong.shop.loyalty.config;

import dev.meirong.shop.common.idempotency.DbIdempotencyGuard;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import dev.meirong.shop.loyalty.domain.LoyaltyIdempotencyKeyRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoyaltyIdempotencyConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdempotencyGuard.class)
    IdempotencyGuard loyaltyDbIdempotencyGuard(LoyaltyIdempotencyKeyRepository idempotencyRepository) {
        return new DbIdempotencyGuard(idempotencyRepository);
    }
}
