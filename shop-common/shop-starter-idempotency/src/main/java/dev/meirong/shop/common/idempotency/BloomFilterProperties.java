package dev.meirong.shop.common.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.idempotency.bloom-filter")
public class BloomFilterProperties {

    private boolean enabled;
    private String redisKey = "";
    private long expectedInsertions = 1_000_000L;
    private double falseProbability = 0.001d;
    private long redisTimeoutMs = 100L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRedisKey() {
        return redisKey;
    }

    public void setRedisKey(String redisKey) {
        this.redisKey = redisKey == null ? "" : redisKey;
    }

    public long getExpectedInsertions() {
        return expectedInsertions;
    }

    public void setExpectedInsertions(long expectedInsertions) {
        this.expectedInsertions = expectedInsertions;
    }

    public double getFalseProbability() {
        return falseProbability;
    }

    public void setFalseProbability(double falseProbability) {
        this.falseProbability = falseProbability;
    }

    public long getRedisTimeoutMs() {
        return redisTimeoutMs;
    }

    public void setRedisTimeoutMs(long redisTimeoutMs) {
        this.redisTimeoutMs = redisTimeoutMs;
    }

    public String requireRedisKey() {
        if (redisKey == null || redisKey.isBlank()) {
            throw new IllegalArgumentException("shop.idempotency.bloom-filter.redis-key must be set");
        }
        return redisKey;
    }
}
