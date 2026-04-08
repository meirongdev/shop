package dev.meirong.shop.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class MetricsAutoConfiguration {

    @Bean
    public MetricsHelper metricsHelper(@Value("${spring.application.name:unknown}") String serviceName,
                                       MeterRegistry meterRegistry) {
        return new MetricsHelper(serviceName, meterRegistry);
    }
}
