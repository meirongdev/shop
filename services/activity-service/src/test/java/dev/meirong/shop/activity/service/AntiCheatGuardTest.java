package dev.meirong.shop.activity.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meirong.shop.activity.config.ActivityProperties;
import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.activity.domain.GameType;
import dev.meirong.shop.activity.support.RedissonTestClientFactory;
import dev.meirong.shop.common.error.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AntiCheatGuardTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4").withExposedPorts(6379);

    private RedissonClient redissonClient;

    @BeforeEach
    void setUp() {
        redissonClient = RedissonTestClientFactory.create(REDIS.getHost(), REDIS.getMappedPort(6379));
        redissonClient.getKeys().flushall();
    }

    @AfterEach
    void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void check_blocksPlayerWhenBurstLimitExceeded() {
        AntiCheatGuard guard = new AntiCheatGuard(
                redissonClient,
                new ActivityProperties("http://loyalty-service:8080",
                        new ActivityProperties.AntiCheat(3, 20, 10, 24, true)),
                new SimpleMeterRegistry());
        ActivityGame game = new ActivityGame("game-1", GameType.RED_ENVELOPE, "Red Envelope");

        guard.check(game, "player-1001", "203.0.113.10", "device-1");
        guard.check(game, "player-1001", "203.0.113.10", "device-1");
        guard.check(game, "player-1001", "203.0.113.10", "device-1");

        assertThatThrownBy(() -> guard.check(game, "player-1001", "203.0.113.10", "device-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Too many participation attempts for this player");
    }

    @Test
    void check_blocksSecondAccountOnSameDeviceFingerprint() {
        AntiCheatGuard guard = new AntiCheatGuard(
                redissonClient,
                new ActivityProperties("http://loyalty-service:8080",
                        new ActivityProperties.AntiCheat(10, 20, 10, 24, true)),
                new SimpleMeterRegistry());
        ActivityGame game = new ActivityGame("game-1", GameType.RED_ENVELOPE, "Red Envelope");

        assertThatCode(() -> guard.check(game, "player-1001", "203.0.113.10", "shared-device"))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> guard.check(game, "player-1002", "203.0.113.11", "shared-device"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Device fingerprint is already bound");
    }
}
