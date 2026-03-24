package dev.meirong.shop.gateway.predicate;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.ServerRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CanaryRequestPredicatesTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final SetOperations<String, String> setOperations = mock(SetOperations.class);

    CanaryRequestPredicatesTest() {
        new CanaryRequestPredicates(redis);
        when(redis.opsForSet()).thenReturn(setOperations);
    }

    @Test
    void matchesWhenPlayerIsWhitelisted() {
        when(setOperations.isMember("gateway:canary:buyer-api", "buyer-100")).thenReturn(true);

        RequestPredicate predicate = CanaryRequestPredicates.canary("buyer-api");

        assertThat(predicate.test(requestWithPlayer("buyer-100"))).isTrue();
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

        assertThat(predicate.test(requestWithPlayer("buyer-100"))).isFalse();
    }

    private ServerRequest requestWithPlayer(String playerId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buyer/orders");
        request.addHeader("X-Player-Id", playerId);
        return ServerRequest.create(request, List.of());
    }
}
