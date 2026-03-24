package dev.meirong.shop.activity.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.activity.domain.ActivityGameRepository;
import dev.meirong.shop.activity.domain.ActivityParticipation;
import dev.meirong.shop.activity.domain.ActivityParticipationRepository;
import dev.meirong.shop.activity.domain.GameType;
import dev.meirong.shop.activity.domain.PrizeType;
import dev.meirong.shop.activity.service.AntiCheatGuard;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.mock;

class GameEngineTest {

    @Test
    void participate_invokesAntiCheatBeforePlugin() {
        ActivityGameRepository gameRepository = mock(ActivityGameRepository.class);
        ActivityParticipationRepository participationRepository = mock(ActivityParticipationRepository.class);
        GamePluginRegistry pluginRegistry = mock(GamePluginRegistry.class);
        AntiCheatGuard antiCheatGuard = mock(AntiCheatGuard.class);
        GamePlugin plugin = mock(GamePlugin.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GameEngine gameEngine = new GameEngine(gameRepository, participationRepository, pluginRegistry, antiCheatGuard, objectMapper, new SimpleMeterRegistry());

        ActivityGame game = new ActivityGame("game-1", GameType.RED_ENVELOPE, "Red Envelope");
        game.activate();
        when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
        when(participationRepository.countByGameIdAndPlayerId("game-1", "player-1001")).thenReturn(0L);
        when(participationRepository.countByGameIdAndPlayerIdSince(eq("game-1"), eq("player-1001"), any())).thenReturn(0L);
        when(pluginRegistry.requirePlugin(GameType.RED_ENVELOPE)).thenReturn(plugin);
        when(plugin.participate(any())).thenReturn(ParticipateResult.win(null, "Red Envelope",
                dev.meirong.shop.activity.domain.PrizeType.POINTS, BigDecimal.ONE, "{\"amount\":\"1.00\"}"));

        gameEngine.participate("game-1", "player-1001", null, "203.0.113.10", "device-1");

        verify(antiCheatGuard).check(game, "player-1001", "203.0.113.10", "device-1");
        verify(plugin).participate(any());
        verify(participationRepository).save(any(ActivityParticipation.class));
    }

    @Test
    void participate_whenAntiCheatRejects_skipsPlugin() {
        ActivityGameRepository gameRepository = mock(ActivityGameRepository.class);
        ActivityParticipationRepository participationRepository = mock(ActivityParticipationRepository.class);
        GamePluginRegistry pluginRegistry = mock(GamePluginRegistry.class);
        AntiCheatGuard antiCheatGuard = mock(AntiCheatGuard.class);
        GamePlugin plugin = mock(GamePlugin.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GameEngine gameEngine = new GameEngine(gameRepository, participationRepository, pluginRegistry, antiCheatGuard, objectMapper, new SimpleMeterRegistry());

        ActivityGame game = new ActivityGame("game-1", GameType.RED_ENVELOPE, "Red Envelope");
        game.activate();
        when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
        BusinessException blocked = new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS, "Too many participation attempts");
        org.mockito.Mockito.doThrow(blocked).when(antiCheatGuard)
                .check(game, "player-1001", "203.0.113.10", "device-1");

        assertThatThrownBy(() -> gameEngine.participate("game-1", "player-1001", null, "203.0.113.10", "device-1"))
                .isSameAs(blocked);

        verify(pluginRegistry, never()).requirePlugin(any());
        verify(participationRepository, never()).save(any());
    }

    @Test
    void participate_withCardPrize_skipsRewardDispatchQueue() {
        ActivityGameRepository gameRepository = mock(ActivityGameRepository.class);
        ActivityParticipationRepository participationRepository = mock(ActivityParticipationRepository.class);
        GamePluginRegistry pluginRegistry = mock(GamePluginRegistry.class);
        AntiCheatGuard antiCheatGuard = mock(AntiCheatGuard.class);
        GamePlugin plugin = mock(GamePlugin.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GameEngine gameEngine = new GameEngine(gameRepository, participationRepository, pluginRegistry, antiCheatGuard, objectMapper, new SimpleMeterRegistry());

        ActivityGame game = new ActivityGame("game-card-1", GameType.COLLECT_CARD, "Collect Cards");
        game.activate();
        when(gameRepository.findById("game-card-1")).thenReturn(Optional.of(game));
        when(participationRepository.countByGameIdAndPlayerId("game-card-1", "player-1001")).thenReturn(0L);
        when(participationRepository.countByGameIdAndPlayerIdSince(eq("game-card-1"), eq("player-1001"), any())).thenReturn(0L);
        when(pluginRegistry.requirePlugin(GameType.COLLECT_CARD)).thenReturn(plugin);
        when(plugin.participate(any())).thenReturn(new ParticipateResult(
                true, "card-dragon", "Dragon", PrizeType.CARD, null,
                "{\"cardId\":\"card-dragon\",\"fullSet\":false}", "Collected new card"));

        gameEngine.participate("game-card-1", "player-1001", null, "203.0.113.10", "device-1");

        ArgumentCaptor<ActivityParticipation> captor = ArgumentCaptor.forClass(ActivityParticipation.class);
        verify(participationRepository).save(captor.capture());
        assertThat(captor.getValue().getRewardStatus()).isEqualTo("SKIPPED");
        assertThat(captor.getValue().getPrizeSnapshot()).contains("\"prizeType\":\"CARD\"");
    }

    @Test
    void participate_withProgressPrize_skipsRewardDispatchQueue() {
        ActivityGameRepository gameRepository = mock(ActivityGameRepository.class);
        ActivityParticipationRepository participationRepository = mock(ActivityParticipationRepository.class);
        GamePluginRegistry pluginRegistry = mock(GamePluginRegistry.class);
        AntiCheatGuard antiCheatGuard = mock(AntiCheatGuard.class);
        GamePlugin plugin = mock(GamePlugin.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GameEngine gameEngine = new GameEngine(gameRepository, participationRepository, pluginRegistry, antiCheatGuard, objectMapper, new SimpleMeterRegistry());

        ActivityGame game = new ActivityGame("game-farm-1", GameType.VIRTUAL_FARM, "Virtual Farm");
        game.activate();
        when(gameRepository.findById("game-farm-1")).thenReturn(Optional.of(game));
        when(participationRepository.countByGameIdAndPlayerId("game-farm-1", "player-1001")).thenReturn(0L);
        when(participationRepository.countByGameIdAndPlayerIdSince(eq("game-farm-1"), eq("player-1001"), any())).thenReturn(0L);
        when(pluginRegistry.requirePlugin(GameType.VIRTUAL_FARM)).thenReturn(plugin);
        when(plugin.participate(any())).thenReturn(new ParticipateResult(
                true, null, "Virtual Farm", PrizeType.PROGRESS, null,
                "{\"stage\":2,\"matured\":false}", "Farm progress +25"));

        gameEngine.participate("game-farm-1", "player-1001", null, "203.0.113.10", "device-1");

        ArgumentCaptor<ActivityParticipation> captor = ArgumentCaptor.forClass(ActivityParticipation.class);
        verify(participationRepository).save(captor.capture());
        assertThat(captor.getValue().getRewardStatus()).isEqualTo("SKIPPED");
        assertThat(captor.getValue().getPrizeSnapshot()).contains("\"prizeType\":\"PROGRESS\"");
    }

    @Test
    void participate_withPointsPrize_keepsPendingRewardStatus() {
        ActivityGameRepository gameRepository = mock(ActivityGameRepository.class);
        ActivityParticipationRepository participationRepository = mock(ActivityParticipationRepository.class);
        GamePluginRegistry pluginRegistry = mock(GamePluginRegistry.class);
        AntiCheatGuard antiCheatGuard = mock(AntiCheatGuard.class);
        GamePlugin plugin = mock(GamePlugin.class);
        ObjectMapper objectMapper = new ObjectMapper();
        GameEngine gameEngine = new GameEngine(gameRepository, participationRepository, pluginRegistry, antiCheatGuard, objectMapper, new SimpleMeterRegistry());

        ActivityGame game = new ActivityGame("game-farm-2", GameType.VIRTUAL_FARM, "Virtual Farm");
        game.activate();
        when(gameRepository.findById("game-farm-2")).thenReturn(Optional.of(game));
        when(participationRepository.countByGameIdAndPlayerId("game-farm-2", "player-1001")).thenReturn(0L);
        when(participationRepository.countByGameIdAndPlayerIdSince(eq("game-farm-2"), eq("player-1001"), any())).thenReturn(0L);
        when(pluginRegistry.requirePlugin(GameType.VIRTUAL_FARM)).thenReturn(plugin);
        when(plugin.participate(any())).thenReturn(new ParticipateResult(
                true, "prize-points", "Farm Points", PrizeType.POINTS, BigDecimal.TEN,
                "{\"action\":\"HARVEST\",\"harvested\":true}", "Farm harvested reward: Farm Points"));

        gameEngine.participate("game-farm-2", "player-1001", "{\"action\":\"HARVEST\"}", "203.0.113.10", "device-1");

        ArgumentCaptor<ActivityParticipation> captor = ArgumentCaptor.forClass(ActivityParticipation.class);
        verify(participationRepository).save(captor.capture());
        assertThat(captor.getValue().getRewardStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getPrizeSnapshot()).contains("\"prizeType\":\"POINTS\"");
    }
}
