package dev.meirong.shop.gateway;

import dev.meirong.shop.common.trace.CorrelationFilter;
import dev.meirong.shop.common.trace.TraceCorrelationResponseFilter;
import dev.meirong.shop.gateway.filter.RateLimitingFilter;
import dev.meirong.shop.gateway.filter.TrustedHeadersFilter;
import dev.meirong.shop.gateway.predicate.CanaryRequestPredicates;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class GatewayContextTest {

    @Container
    @ServiceConnection(name = "redis")
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4"))
            .withExposedPorts(6379);

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsGatewayMvcBeans() {
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
        assertThat(context.getBeansOfType(CorrelationFilter.class)).hasSize(1);
        assertThat(context.getBeansOfType(TraceCorrelationResponseFilter.class)).hasSize(1);
        assertThat(context.getBeansOfType(TrustedHeadersFilter.class)).hasSize(1);
        assertThat(context.getBeansOfType(RateLimitingFilter.class)).hasSize(1);
        assertThat(context.getBeansOfType(CanaryRequestPredicates.class)).hasSize(1);
    }
}
