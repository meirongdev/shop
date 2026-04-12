package dev.meirong.shop.gateway.predicate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.server.mvc.predicate.PredicateSupplier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;

/**
 * Supplies the {@code Canary} predicate used in YAML route configuration.
 *
 * <p>Canary eligibility is determined by a Redis Set ({@code gateway:canary:{routeId}}).
 * Results are cached locally for {@value #CACHE_TTL_SECONDS} seconds per
 * (routeId, buyerId) pair to reduce Redis round-trips on hot paths.
 *
 * <p>On Redis failure the predicate returns {@code false} (routes to the stable version).
 */
@Component
public class CanaryRequestPredicates implements PredicateSupplier {

    private static final Logger log = LoggerFactory.getLogger(CanaryRequestPredicates.class);
    private static final String BUYER_ID = "X-Buyer-Id";
    private static final String KEY_PREFIX = "gateway:canary:";
    private static final int CACHE_TTL_SECONDS = 10;

    // Static references are required because Spring Cloud Gateway MVC calls the predicate
    // factory methods statically during route resolution.
    private static final AtomicReference<StringRedisTemplate> redisRef = new AtomicReference<>();
    static final Cache<String, Boolean> localCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(CACHE_TTL_SECONDS))
            .build();

    public CanaryRequestPredicates(StringRedisTemplate redisTemplate) {
        CanaryRequestPredicates.redisRef.set(redisTemplate);
    }

    // SCG MVC property-based routes invoke predicate operations as static methods.
    public static RequestPredicate canary(String routeId) {
        return request -> {
            String buyerId = request.servletRequest().getHeader(BUYER_ID);
            if (buyerId == null || buyerId.isBlank() || redisRef.get() == null) {
                return false;
            }
            String cacheKey = routeId + ':' + buyerId;
            Boolean cached = localCache.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }
            try {
                boolean member = Boolean.TRUE.equals(
                        redisRef.get().opsForSet().isMember(KEY_PREFIX + routeId, buyerId));
                localCache.put(cacheKey, member);
                return member;
            } catch (DataAccessException exception) {
                log.warn("Canary lookup failed for route {} and buyer {}, routing to stable: {}",
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
