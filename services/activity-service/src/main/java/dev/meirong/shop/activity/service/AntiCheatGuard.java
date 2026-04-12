package dev.meirong.shop.activity.service;

import dev.meirong.shop.activity.config.ActivityProperties;
import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

@Component
public class AntiCheatGuard {

    private final RedissonClient redissonClient;
    private final ActivityProperties properties;
    private final MeterRegistry meterRegistry;

    public AntiCheatGuard(RedissonClient redissonClient,
                          ActivityProperties properties,
                          MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public void check(ActivityGame game, String buyerId, String ipAddress, String deviceFingerprint) {
        if (buyerId == null || buyerId.isBlank()) {
            return;
        }
        checkPlayerRateLimit(game.getId(), buyerId);
        checkIpRateLimit(game.getId(), ipAddress);
        checkDeviceReuse(game.getId(), buyerId, deviceFingerprint);
    }

    private void checkPlayerRateLimit(String gameId, String buyerId) {
        long count = incrementWithinWindow("activity:ac:player:%s:%s".formatted(gameId, buyerId));
        if (count > properties.antiCheat().playerRequestsPerWindow()) {
            recordBlock("player_rate_limit");
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,
                    "Too many participation attempts for this player");
        }
    }

    private void checkIpRateLimit(String gameId, String ipAddress) {
        String normalizedIp = normalizeIp(ipAddress);
        if (normalizedIp == null) {
            return;
        }
        long count = incrementWithinWindow("activity:ac:ip:%s:%s".formatted(gameId, normalizedIp));
        if (count > properties.antiCheat().ipRequestsPerWindow()) {
            recordBlock("ip_rate_limit");
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,
                    "Too many participation attempts from this IP");
        }
    }

    private void checkDeviceReuse(String gameId, String buyerId, String deviceFingerprint) {
        if (!properties.antiCheat().deviceFingerprintEnabled()
                || deviceFingerprint == null
                || deviceFingerprint.isBlank()) {
            return;
        }
        String key = "activity:ac:device:%s:%s".formatted(gameId, deviceFingerprint);
        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        String existingPlayer = bucket.get();
        if (existingPlayer == null) {
            bucket.set(buyerId, Duration.ofHours(properties.antiCheat().deviceFingerprintTtlHours()));
            return;
        }
        if (!existingPlayer.equals(buyerId)) {
            recordBlock("device_reuse");
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "Device fingerprint is already bound to another participant");
        }
    }

    private long incrementWithinWindow(String key) {
        RAtomicLong counter = redissonClient.getAtomicLong(key);
        long count = counter.incrementAndGet();
        if (count == 1L) {
            counter.expire(Duration.ofSeconds(properties.antiCheat().windowSeconds()));
        }
        return count;
    }

    private void recordBlock(String reason) {
        meterRegistry.counter("activity_anti_cheat_blocked_total", "reason", reason).increment();
    }

    private String normalizeIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        return ipAddress.split(",")[0].trim();
    }
}
