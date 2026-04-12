package dev.meirong.shop.gateway.filter;

import dev.meirong.shop.gateway.config.GatewayProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
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

/**
 * Token-bucket rate limiter for {@code /api/**} routes.
 *
 * <p>Each client (identified by {@code X-Buyer-Id} or remote IP) gets a bucket that
 * refills at {@code requestsPerMinute / 60} tokens/second up to a {@code burst} ceiling.
 * Two Redis keys are used per bucket: {@code rl:{id}:tokens} (remaining float) and
 * {@code rl:{id}:ts} (last-refill epoch ms). Both are updated atomically via a Lua script.
 *
 * <p>On Redis failure the filter fails open (allows the request) to preserve availability.
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 20)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final String BUYER_ID = "X-Buyer-Id";
    private static final String KEY_PREFIX = "rl:";

    /**
     * Token-bucket Lua script.
     *
     * <p>KEYS[1] = tokens key, KEYS[2] = timestamp key<br>
     * ARGV[1] = rate (tokens/sec), ARGV[2] = capacity (burst), ARGV[3] = now (epoch ms)<br>
     * Returns 1 if the request is allowed, 0 if rate-limited.
     */
    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT = buildTokenBucketScript();

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
            String baseKey = resolveRateLimitKey(request);
            double ratePerSec = (double) properties.rateLimit().requestsPerMinute() / 60.0;
            long capacity = properties.rateLimit().burst();
            long nowMs = clock.instant().toEpochMilli();

            Long allowed = redis.execute(
                    TOKEN_BUCKET_SCRIPT,
                    List.of(baseKey + ":tokens", baseKey + ":ts"),
                    String.valueOf(ratePerSec),
                    String.valueOf(capacity),
                    String.valueOf(nowMs));

            if (allowed == null || allowed == 0L) {
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
        String buyerId = request.getHeader(BUYER_ID);
        String id = (buyerId != null && !buyerId.isBlank())
                ? buyerId
                : (request.getRemoteAddr() == null || request.getRemoteAddr().isBlank() ? "unknown" : request.getRemoteAddr());
        return KEY_PREFIX + id;
    }

    private static DefaultRedisScript<Long> buildTokenBucketScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("""
                local tokens_key = KEYS[1]
                local ts_key     = KEYS[2]
                local rate       = tonumber(ARGV[1])
                local capacity   = tonumber(ARGV[2])
                local now        = tonumber(ARGV[3])

                local last_tokens = tonumber(redis.call('get', tokens_key))
                if last_tokens == nil then last_tokens = capacity end

                local last_ts = tonumber(redis.call('get', ts_key))
                if last_ts == nil then last_ts = now end

                local elapsed_ms = math.max(0, now - last_ts)
                local refilled   = math.min(capacity, last_tokens + elapsed_ms * rate / 1000.0)

                if refilled < 1.0 then
                    return 0
                end

                local ttl = math.ceil(capacity / rate * 2)
                redis.call('setex', tokens_key, ttl, refilled - 1)
                redis.call('setex', ts_key, ttl, now)
                return 1
                """);
        script.setResultType(Long.class);
        return script;
    }
}
