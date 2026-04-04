package dev.meirong.shop.gateway.predicate;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateSupplier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;

@Component
public class CanaryRequestPredicates implements PredicateSupplier {

    private static final Logger log = LoggerFactory.getLogger(CanaryRequestPredicates.class);
    private static final String BUYER_ID = "X-Buyer-Id";
    private static final String KEY_PREFIX = "gateway:canary:";
    private static volatile StringRedisTemplate redisTemplate;

    public CanaryRequestPredicates(StringRedisTemplate redisTemplate) {
        CanaryRequestPredicates.redisTemplate = redisTemplate;
    }

    // SCG MVC property-based routes invoke predicate operations as static methods.
    public static RequestPredicate canary(String routeId) {
        return request -> {
            String buyerId = request.servletRequest().getHeader(BUYER_ID);
            if (buyerId == null || buyerId.isBlank() || redisTemplate == null) {
                return false;
            }
            try {
                return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(KEY_PREFIX + routeId, buyerId));
            } catch (DataAccessException exception) {
                log.warn("Canary lookup failed for route {} and player {}, routing to stable: {}",
                        routeId, buyerId, exception.getMessage());
                return false;
            }
        };
    }

    @Override
    public Collection<Method> get() {
        return Arrays.stream(CanaryRequestPredicates.class.getMethods())
                .filter(method -> method.getReturnType().equals(RequestPredicate.class))
                .toList();
    }
}
