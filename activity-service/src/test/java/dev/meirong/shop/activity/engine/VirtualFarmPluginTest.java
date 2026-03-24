package dev.meirong.shop.activity.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.activity.domain.ActivityRewardPrize;
import dev.meirong.shop.activity.domain.ActivityRewardPrizeRepository;
import dev.meirong.shop.activity.domain.ActivityVirtualFarm;
import dev.meirong.shop.activity.domain.ActivityVirtualFarmRepository;
import dev.meirong.shop.activity.domain.GameType;
import dev.meirong.shop.activity.domain.PrizeType;
import dev.meirong.shop.common.error.BusinessException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VirtualFarmPluginTest {

    @Test
    void waterThenHarvest_virtualFarmFlowWorks() {
        ActivityVirtualFarmRepository farmRepository = mock(ActivityVirtualFarmRepository.class);
        ActivityRewardPrizeRepository prizeRepository = mock(ActivityRewardPrizeRepository.class);
        List<ActivityVirtualFarm> farms = new ArrayList<>();
        List<ActivityRewardPrize> prizes = new ArrayList<>();
        wireFarmRepository(farmRepository, farms);
        wirePrizeRepository(prizeRepository, prizes);
        ActivityRewardPrize reward = new ActivityRewardPrize("prize-points", "farm-game-1", "Farm Points", PrizeType.POINTS);
        reward.setValue(BigDecimal.valueOf(20));
        reward.setProbability(BigDecimal.ONE);
        prizes.add(reward);

        VirtualFarmPlugin plugin = new VirtualFarmPlugin(farmRepository, prizeRepository, new ObjectMapper());
        ActivityGame game = new ActivityGame("farm-game-1", GameType.VIRTUAL_FARM, "Kind Farm");
        game.setConfig("""
                {
                  "max_stage": 2,
                  "stage_progress": 50,
                  "water_progress": 50
                }
                """);

        plugin.initialize(game);

        ParticipateResult first = plugin.participate(new ParticipateContext(
                game.getId(), GameType.VIRTUAL_FARM, "player-1001", null, game.getConfig(), null, null));
        ParticipateResult second = plugin.participate(new ParticipateContext(
                game.getId(), GameType.VIRTUAL_FARM, "player-1001", null, game.getConfig(), null, null));
        ParticipateResult harvest = plugin.participate(new ParticipateContext(
                game.getId(), GameType.VIRTUAL_FARM, "player-1001", null, game.getConfig(), "{\"action\":\"HARVEST\"}", null));
        ParticipateResult repeatedHarvest = plugin.participate(new ParticipateContext(
                game.getId(), GameType.VIRTUAL_FARM, "player-1001", null, game.getConfig(), "{\"action\":\"HARVEST\"}", null));

        assertThat(first.win()).isTrue();
        assertThat(first.prizeType()).isEqualTo(PrizeType.PROGRESS);
        assertThat(first.message()).isEqualTo("Farm progress +50");
        assertThat(first.animationHint()).contains("\"matured\":false");

        assertThat(second.win()).isTrue();
        assertThat(second.prizeType()).isEqualTo(PrizeType.PROGRESS);
        assertThat(second.message()).isEqualTo("Farm matured. Use action HARVEST to claim reward");
        assertThat(second.animationHint()).contains("\"matured\":true");
        assertThat(second.animationHint()).contains("\"harvested\":false");

        assertThat(harvest.win()).isTrue();
        assertThat(harvest.prizeType()).isEqualTo(PrizeType.POINTS);
        assertThat(harvest.prizeName()).isEqualTo("Farm Points");
        assertThat(harvest.message()).isEqualTo("Farm harvested reward: Farm Points");
        assertThat(harvest.animationHint()).contains("\"action\":\"HARVEST\"");
        assertThat(harvest.animationHint()).contains("\"harvested\":true");

        assertThat(repeatedHarvest.win()).isFalse();
        assertThat(repeatedHarvest.message()).isEqualTo("Farm reward has already been claimed");

        assertThat(farms).hasSize(1);
        assertThat(farms.getFirst().isHarvested()).isTrue();
        assertThat(prizes.getFirst().getRemainingStock()).isEqualTo(-1);
        assertThat(plugin.extensionTablePrefix()).contains("virtual_farm");
    }

    @Test
    void initialize_withoutHarvestReward_throwsValidationError() {
        ActivityVirtualFarmRepository farmRepository = mock(ActivityVirtualFarmRepository.class);
        ActivityRewardPrizeRepository prizeRepository = mock(ActivityRewardPrizeRepository.class);
        when(prizeRepository.findByGameIdOrderByDisplayOrderAsc(anyString())).thenReturn(List.of());

        VirtualFarmPlugin plugin = new VirtualFarmPlugin(farmRepository, prizeRepository, new ObjectMapper());
        ActivityGame game = new ActivityGame("farm-game-2", GameType.VIRTUAL_FARM, "Broken Farm");
        game.setConfig("""
                {
                  "max_stage": 2,
                  "stage_progress": 50,
                  "water_progress": 25
                }
                """);

        assertThatThrownBy(() -> plugin.initialize(game))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("requires at least one non-NOTHING harvest reward");
    }

    @Test
    void harvest_beforeMature_returnsMiss() {
        ActivityVirtualFarmRepository farmRepository = mock(ActivityVirtualFarmRepository.class);
        ActivityRewardPrizeRepository prizeRepository = mock(ActivityRewardPrizeRepository.class);
        List<ActivityVirtualFarm> farms = new ArrayList<>();
        List<ActivityRewardPrize> prizes = new ArrayList<>();
        wireFarmRepository(farmRepository, farms);
        wirePrizeRepository(prizeRepository, prizes);
        prizes.add(new ActivityRewardPrize("prize-points", "farm-game-3", "Farm Points", PrizeType.POINTS));

        VirtualFarmPlugin plugin = new VirtualFarmPlugin(farmRepository, prizeRepository, new ObjectMapper());
        ActivityGame game = new ActivityGame("farm-game-3", GameType.VIRTUAL_FARM, "Farm");
        game.setConfig("""
                {
                  "max_stage": 2,
                  "stage_progress": 50,
                  "water_progress": 20
                }
                """);

        plugin.initialize(game);
        ParticipateResult harvest = plugin.participate(new ParticipateContext(
                game.getId(), GameType.VIRTUAL_FARM, "player-1001", null, game.getConfig(), "{\"action\":\"HARVEST\"}", null));

        assertThat(harvest.win()).isFalse();
        assertThat(harvest.message()).isEqualTo("Farm is still growing");
    }

    private void wireFarmRepository(ActivityVirtualFarmRepository farmRepository, List<ActivityVirtualFarm> farms) {
        when(farmRepository.findByGameIdAndPlayerId(anyString(), anyString())).thenAnswer(invocation -> {
            String gameId = invocation.getArgument(0, String.class);
            String playerId = invocation.getArgument(1, String.class);
            return farms.stream()
                    .filter(farm -> farm.getGameId().equals(gameId) && farm.getPlayerId().equals(playerId))
                    .findFirst();
        });

        when(farmRepository.save(any(ActivityVirtualFarm.class))).thenAnswer(invocation -> {
            ActivityVirtualFarm farm = invocation.getArgument(0, ActivityVirtualFarm.class);
            farms.removeIf(existing -> existing.getId().equals(farm.getId()));
            farms.add(farm);
            return farm;
        });
    }

    private void wirePrizeRepository(ActivityRewardPrizeRepository prizeRepository, List<ActivityRewardPrize> prizes) {
        when(prizeRepository.findByGameIdOrderByDisplayOrderAsc(anyString())).thenAnswer(invocation -> {
            String gameId = invocation.getArgument(0, String.class);
            return prizes.stream()
                    .filter(prize -> prize.getGameId().equals(gameId))
                    .sorted((left, right) -> Integer.compare(left.getDisplayOrder(), right.getDisplayOrder()))
                    .toList();
        });

        doAnswer(invocation -> invocation.getArgument(0, ActivityRewardPrize.class))
                .when(prizeRepository).save(any(ActivityRewardPrize.class));
    }
}
