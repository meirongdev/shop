package dev.meirong.shop.gateway.predicate;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CanaryRequestPredicatesTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final SetOperations<String, String> setOperations = mock(SetOperations.class);

    @BeforeEach
    void setUp() {
        new CanaryRequestPredicates(redis);
        when(redis.opsForSet()).thenReturn(setOperations);
        // Invalidate the local cache between tests so each test starts fresh.
        CanaryRequestPredicates.localCache.invalidateAll();
    }

    @Test
    void matchesWhenPlayerIsWhitelisted() {
        when(setOperations.isMember("gateway:canary:buyer-api", "buyer-100")).thenReturn(true);

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
        when(setOperations.isMember("gateway:canary:buyer-api", "buyer-100"))
                .thenThrow(new DataAccessResourceFailureException("redis down"));

        RequestPredicate predicate = CanaryRequestPredicates.canary("buyer-api");

        assertThat(predicate.test(requestWithBuyer("buyer-100"))).isFalse();
    }

    @Test
    void cachesPreviouslyResolvedCanaryDecision() {
        when(setOperations.isMember("gateway:canary:buyer-api", "buyer-100")).thenReturn(true);
        RequestPredicate predicate = CanaryRequestPredicates.canary("buyer-api");

        predicate.test(requestWithBuyer("buyer-100"));
        predicate.test(requestWithBuyer("buyer-100"));

        verify(setOperations, times(1)).isMember("gateway:canary:buyer-api", "buyer-100");
    }

    private ServerRequest requestWithBuyer(String buyerId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buyer/orders");
        request.addHeader("X-Buyer-Id", buyerId);
        return ServerRequest.create(request, List.of());
    }
}
