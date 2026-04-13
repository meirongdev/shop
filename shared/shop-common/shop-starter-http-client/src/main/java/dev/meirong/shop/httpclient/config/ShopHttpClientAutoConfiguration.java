package dev.meirong.shop.httpclient.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.httpclient.interceptor.TracingHeaderInterceptor;
import dev.meirong.shop.httpclient.interceptor.TracingHeaderInterceptor.BaggageMapping;
import dev.meirong.shop.httpclient.support.ShopHttpExchangeSupport;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClient;

/**
 * Auto-configuration for shared HTTP client infrastructure.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link TracingHeaderInterceptor} — propagates baggage fields as HTTP headers</li>
 *   <li>{@link ShopHttpExchangeSupport} — factory for creating {@code @HttpExchange} clients</li>
 * </ul>
 *
 * <p>The {@code RestClient.Builder} used is the auto-configured one from Spring Boot,
 * which includes {@code ObservationRestClientCustomizer} for Micrometer Tracing.
 *
 * @see <a href="https://docs.spring.io/spring-boot/reference/actuator/tracing.html">Spring Boot Tracing Docs</a>
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
public class ShopHttpClientAutoConfiguration {

    @Bean
    @ConditionalOnBean(RestClient.Builder.class)
    public TracingHeaderInterceptor tracingHeaderInterceptor(
            @Value("${shop.http-client.baggage-headers:X-Buyer-Id,X-Seller-Id,X-Username,X-Portal,X-Roles,X-Order-Id}")
            @Nullable String baggageHeaders) {
        if (baggageHeaders == null || baggageHeaders.isBlank()) {
            return new TracingHeaderInterceptor(BaggageMapping.defaults());
        }
        List<BaggageMapping> mappings = List.of(baggageHeaders.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(BaggageMapping::of)
                .toList();
        return new TracingHeaderInterceptor(mappings);
    }

    @Bean
    @ConditionalOnBean({RestClient.Builder.class, TracingHeaderInterceptor.class})
    public ShopHttpExchangeSupport shopHttpExchangeSupport(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            TracingHeaderInterceptor tracingInterceptor,
            @Value("${shop.http-client.connect-timeout:2s}") @Nullable Duration connectTimeout,
            @Value("${shop.http-client.read-timeout:5s}") @Nullable Duration readTimeout) {
        return new ShopHttpExchangeSupport(
                restClientBuilder,
                objectMapper,
                tracingInterceptor,
                connectTimeout,
                readTimeout);
    }
}
