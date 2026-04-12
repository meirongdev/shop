package dev.meirong.shop.activity.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.activity.domain.ActivityParticipationRepository;
import dev.meirong.shop.activity.domain.GameType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RedEnvelopePluginTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4").withExposedPorts(6379);

    private RedissonClient redissonClient;

    @BeforeEach
    void setUp() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redissonClient = Redisson.create(config);
        redissonClient.getKeys().flushall();
    }

    @AfterEach
    void tearDown() {
        if (redissonClient != null) {
            redissonClient.shutdown();
        }
    }

    @Test
    void initializeParticipateAndSettle_redEnvelopeFlowWorks() {
        ActivityParticipationRepository participationRepository = mock(ActivityParticipationRepository.class);
        RedEnvelopePlugin plugin = new RedEnvelopePlugin(redissonClient, new ObjectMapper(), participationRepository);
        ActivityGame game = new ActivityGame("game-red-1", GameType.RED_ENVELOPE, "Flash Red Envelope");
        game.setConfig("{\"packet_count\":3,\"total_amount\":6.00}");

        plugin.initialize(game);

        List<BigDecimal> claimedAmounts = new ArrayList<>();
        ParticipateResult first = plugin.participate(new ParticipateContext(game.getId(), GameType.RED_ENVELOPE,
                "player-1001", null, game.getConfig(), null, null));
        claimedAmounts.add(first.prizeValue());
        ParticipateResult duplicate = plugin.participate(new ParticipateContext(game.getId(), GameType.RED_ENVELOPE,
                "player-1001", null, game.getConfig(), null, null));
        ParticipateResult second = plugin.participate(new ParticipateContext(game.getId(), GameType.RED_ENVELOPE,
                "player-1002", null, game.getConfig(), null, null));
        claimedAmounts.add(second.prizeValue());
        ParticipateResult third = plugin.participate(new ParticipateContext(game.getId(), GameType.RED_ENVELOPE,
                "player-1003", null, game.getConfig(), null, null));
        claimedAmounts.add(third.prizeValue());
        ParticipateResult exhausted = plugin.participate(new ParticipateContext(game.getId(), GameType.RED_ENVELOPE,
                "player-1004", null, game.getConfig(), null, null));

        assertThat(first.win()).isTrue();
        assertThat(first.prizeType()).isEqualTo(dev.meirong.shop.activity.domain.PrizeType.POINTS);
        assertThat(duplicate.win()).isFalse();
        assertThat(duplicate.message()).contains("already claimed");
        assertThat(exhausted.win()).isFalse();
        assertThat(exhausted.message()).contains("have been claimed");
        assertThat(claimedAmounts).allMatch(amount -> amount.compareTo(BigDecimal.ZERO) > 0);
        assertThat(claimedAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("6.00");
        assertThat(redissonClient.getMap("re:claims:" + game.getId(), StringCodec.INSTANCE).size()).isEqualTo(3);

        when(participationRepository.countWinningParticipationsByGameId(game.getId())).thenReturn(3L);
        plugin.settle(game);

        assertThat(redissonClient.getBucket("re:packets:" + game.getId()).isExists()).isFalse();
        assertThat(redissonClient.getBucket("re:claims:" + game.getId()).isExists()).isFalse();
    }
}
