package dev.meirong.shop.gateway.filter;

import dev.meirong.shop.gateway.config.GatewayProperties;
import dev.meirong.shop.gateway.support.CapturingFilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimitingFilterTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RScript script = mock(RScript.class);
    private final GatewayProperties properties = new GatewayProperties(
            "http://localhost/.well-known/jwks.json",
            new GatewayProperties.RateLimit(100, 20),
            null);
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-22T05:00:00Z"), ZoneOffset.UTC);
    private final RateLimitingFilter filter = new RateLimitingFilter(redissonClient, properties, clock);

    @BeforeEach
    void setUp() {
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
    }

    @Test
    void allowsRequestWhenWithinLimit() throws Exception {
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), anyList(), any(), any(), any()))
                .thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buyer/orders");
        request.addHeader("X-Buyer-Id", "buyer-100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked()).isTrue();
        ArgumentCaptor<List<Object>> keys = ArgumentCaptor.forClass(List.class);
        verify(script).eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), keys.capture(), any(), any(), any());
        assertThat(keys.getValue()).containsExactly("rl:buyer-100:tokens", "rl:buyer-100:ts");
    }

    @Test
    void rejectsRequestWhenThresholdIsExceeded() throws Exception {
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), anyList(), any(), any(), any()))
                .thenReturn(0L);
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
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), anyList(), any(), any(), any()))
                .thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buyer/orders");
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        ArgumentCaptor<List<Object>> keys = ArgumentCaptor.forClass(List.class);
        verify(script).eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), keys.capture(), any(), any(), any());
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
        verifyNoInteractions(redissonClient, script);
    }

    @Test
    void failsOpenWhenRedisErrors() throws Exception {
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.INTEGER), anyList(), any(), any(), any()))
                .thenThrow(new RedisException("redis down"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buyer/orders");
        request.addHeader("X-Buyer-Id", "buyer-100");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
