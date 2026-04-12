package dev.meirong.shop.gateway.predicate;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CanaryRequestPredicatesTest {

    @SuppressWarnings("unchecked")
    private final RSet<String> rSet = mock(RSet.class);
    private final RedissonClient redisson = mock(RedissonClient.class);

    @BeforeEach
    void setUp() {
        new CanaryRequestPredicates(redisson);
        doReturn(rSet).when(redisson).getSet("gateway:canary:buyer-api", StringCodec.INSTANCE);
        // Invalidate the local cache between tests so each test starts fresh.
        CanaryRequestPredicates.localCache.invalidateAll();
    }

    @Test
    void matchesWhenPlayerIsWhitelisted() {
        when(rSet.contains("buyer-100")).thenReturn(true);

        RequestPredicate predicate = CanaryRequestPredicates.canary("buyer-api");

        assertThat(predicate.test(requestWithBuyer("buyer-100"))).isTrue();
    }

    @Test
    void returnsFalseWhenPlayerIsMissing() {
        RequestPredicate predicate = CanaryRequestPredicates.canary("buyer-api");

        assertThat(predicate.test(ServerRequest.create(new MockHttpServletRequest("GET", "/api/buyer/orders"), List.of()))).isFalse();
    }

    @Test
    void degradesToStableRouteWhenRedisFails() {
        when(rSet.contains("buyer-100"))
                .thenThrow(new RedisException("redis down"));

        RequestPredicate predicate = CanaryRequestPredicates.canary("buyer-api");

        assertThat(predicate.test(requestWithBuyer("buyer-100"))).isFalse();
    }

    @Test
    void cachesPreviouslyResolvedCanaryDecision() {
        when(rSet.contains("buyer-100")).thenReturn(true);
        RequestPredicate predicate = CanaryRequestPredicates.canary("buyer-api");

        predicate.test(requestWithBuyer("buyer-100"));
        predicate.test(requestWithBuyer("buyer-100"));

        verify(rSet, times(1)).contains("buyer-100");
    }

    private ServerRequest requestWithBuyer(String buyerId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buyer/orders");
        request.addHeader("X-Buyer-Id", buyerId);
        return ServerRequest.create(request, List.of());
    }
}
