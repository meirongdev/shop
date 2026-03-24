package dev.meirong.shop.gateway.filter;

import dev.meirong.shop.gateway.config.GatewayProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 20)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final String PLAYER_ID = "X-Player-Id";
    private static final String KEY_PREFIX = "rl:";
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = buildRateLimitScript();

    private final StringRedisTemplate redis;
    private final GatewayProperties properties;
    private final Clock clock;

    @Autowired
    public RateLimitingFilter(StringRedisTemplate redis, GatewayProperties properties) {
        this(redis, properties, Clock.systemUTC());
    }

    RateLimitingFilter(StringRedisTemplate redis, GatewayProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String limitKey = resolveRateLimitKey(request);
            Long count = redis.execute(RATE_LIMIT_SCRIPT, List.of(limitKey), "120");
            if (count != null && count > properties.rateLimit().requestsPerMinute()) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                return;
            }
        } catch (DataAccessException exception) {
            log.warn("Rate limit check failed, allowing request through: {}", exception.getMessage());
        }

        chain.doFilter(request, response);
    }

    private String resolveRateLimitKey(HttpServletRequest request) {
        String playerId = request.getHeader(PLAYER_ID);
        String bucketKey = (playerId != null && !playerId.isBlank())
                ? playerId
                : (request.getRemoteAddr() == null || request.getRemoteAddr().isBlank() ? "unknown" : request.getRemoteAddr());
        return KEY_PREFIX + bucketKey + ':' + currentMinuteBucket();
    }

    private String currentMinuteBucket() {
        Instant now = clock.instant();
        return String.valueOf(now.toEpochMilli() / 60_000);
    }

    private static DefaultRedisScript<Long> buildRateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
                local count = redis.call('INCR', KEYS[1])
                if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end
                return count
                """);
        script.setResultType(Long.class);
        return script;
    }
}
