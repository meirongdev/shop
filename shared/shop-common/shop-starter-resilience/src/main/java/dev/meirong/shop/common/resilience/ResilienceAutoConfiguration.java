package dev.meirong.shop.common.resilience;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = {
        "io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration",
        "io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration",
        "io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration",
        "io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration"
})
@ConditionalOnClass({
        CircuitBreakerRegistry.class,
        RetryRegistry.class,
        BulkheadRegistry.class,
        TimeLimiterRegistry.class
})
public class ResilienceAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnBean({CircuitBreakerRegistry.class, RetryRegistry.class,
                        BulkheadRegistry.class, TimeLimiterRegistry.class})
    ResilienceHelper resilienceHelper(CircuitBreakerRegistry circuitBreakerRegistry,
                                      RetryRegistry retryRegistry,
                                      BulkheadRegistry bulkheadRegistry,
                                      TimeLimiterRegistry timeLimiterRegistry) {
        return new ResilienceHelper(circuitBreakerRegistry, retryRegistry, bulkheadRegistry, timeLimiterRegistry);
    }
}
