package dev.meirong.shop.gateway.filter;

import dev.meirong.shop.gateway.config.GatewayProperties;
import dev.meirong.shop.gateway.support.CapturingFilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimitingFilterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final GatewayProperties properties = new GatewayProperties(
            "secret",
            new GatewayProperties.RateLimit(100, 20),
            null);
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-22T05:00:00Z"), ZoneOffset.UTC);
    private final RateLimitingFilter filter = new RateLimitingFilter(redis, properties, clock);

    @Test
    void allowsRequestWhenWithinLimit() throws Exception {
        when(redis.execute(anyScript(), anyList(), any(), any(), any())).thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buyer/orders");
        request.addHeader("X-Buyer-Id", "buyer-100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked()).isTrue();
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(anyScript(), keys.capture(), any(), any(), any());
        assertThat(keys.getValue()).containsExactly("rl:buyer-100:tokens", "rl:buyer-100:ts");
    }

    @Test
    void rejectsRequestWhenThresholdIsExceeded() throws Exception {
        when(redis.execute(anyScript(), anyList(), any(), any(), any())).thenReturn(0L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buyer/orders");
        request.addHeader("X-Buyer-Id", "buyer-100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked()).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void fallsBackToClientIpWhenPlayerIdIsMissing() throws Exception {
        when(redis.execute(anyScript(), anyList(), any(), any(), any())).thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buyer/orders");
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(anyScript(), keys.capture(), any(), any(), any());
        assertThat(keys.getValue()).containsExactly("rl:10.0.0.5:tokens", "rl:10.0.0.5:ts");
        assertThat(chain.invoked()).isTrue();
    }

    @Test
    void skipsNonApiRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/buyer/home");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked()).isTrue();
        verifyNoInteractions(redis);
    }

    @Test
    void failsOpenWhenRedisErrors() throws Exception {
        when(redis.execute(anyScript(), anyList(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("redis down"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buyer/orders");
        request.addHeader("X-Buyer-Id", "buyer-100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @SuppressWarnings("unchecked")
    private RedisScript<Long> anyScript() {
        return (RedisScript<Long>) any(RedisScript.class);
    }
}
