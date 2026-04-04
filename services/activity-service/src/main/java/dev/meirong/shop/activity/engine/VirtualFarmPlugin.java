package dev.meirong.shop.activity.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.activity.domain.ActivityRewardPrize;
import dev.meirong.shop.activity.domain.ActivityRewardPrizeRepository;
import dev.meirong.shop.activity.domain.ActivityVirtualFarm;
import dev.meirong.shop.activity.domain.ActivityVirtualFarmRepository;
import dev.meirong.shop.activity.domain.GameType;
import dev.meirong.shop.activity.domain.PrizeType;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class VirtualFarmPlugin implements GamePlugin {

    private final ActivityVirtualFarmRepository farmRepository;
    private final ActivityRewardPrizeRepository prizeRepository;
    private final ObjectMapper objectMapper;

    public VirtualFarmPlugin(ActivityVirtualFarmRepository farmRepository,
                             ActivityRewardPrizeRepository prizeRepository,
                             ObjectMapper objectMapper) {
        this.farmRepository = farmRepository;
        this.prizeRepository = prizeRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public GameType supportedType() {
        return GameType.VIRTUAL_FARM;
    }

    @Override
    public void initialize(ActivityGame game) {
        parseConfig(game.getConfig());
        requireHarvestReward(game.getId());
    }

    @Override
    public ParticipateResult participate(ParticipateContext ctx) {
        if (ctx.buyerId() == null || ctx.buyerId().isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Virtual farm requires a buyerId");
        }

        FarmConfig config = parseConfig(ctx.gameConfig());
        FarmAction action = parseAction(ctx.payload());
        ActivityVirtualFarm farm = farmRepository.findByGameIdAndBuyerId(ctx.gameId(), ctx.buyerId())
                .orElseGet(() -> new ActivityVirtualFarm(
                        UUID.randomUUID().toString(), ctx.gameId(), ctx.buyerId(),
                        config.maxStage(), config.stageProgress()));

        if (action == FarmAction.HARVEST) {
            return harvest(ctx.gameId(), farm);
        }

        if (farm.isHarvested()) {
            return ParticipateResult.miss("Farm reward has already been claimed");
        }
        if (farm.isMatured()) {
            return new ParticipateResult(true, null, "Virtual Farm", PrizeType.PROGRESS, null,
                    buildAnimationHint(farm, FarmAction.WATER),
                    "Farm matured. Use action HARVEST to claim reward");
        }

        farm.water(config.waterProgress());
        farmRepository.save(farm);

        if (farm.isMatured()) {
            return new ParticipateResult(true, null, "Virtual Farm", PrizeType.PROGRESS, null,
                    buildAnimationHint(farm, FarmAction.WATER),
                    "Farm matured. Use action HARVEST to claim reward");
        }

        return new ParticipateResult(true, null, "Virtual Farm", PrizeType.PROGRESS, null,
                buildAnimationHint(farm, FarmAction.WATER),
                "Farm progress +%d".formatted(config.waterProgress()));
    }

    @Override
    public Optional<String> extensionTablePrefix() {
        return Optional.of("virtual_farm");
    }

    private ParticipateResult harvest(String gameId, ActivityVirtualFarm farm) {
        if (farm.isHarvested()) {
            return ParticipateResult.miss("Farm reward has already been claimed");
        }
        if (!farm.isMatured()) {
            return ParticipateResult.miss("Farm is still growing");
        }

        ActivityRewardPrize reward = prizeRepository.findByGameIdOrderByDisplayOrderAsc(gameId).stream()
                .filter(prize -> prize.getType() != PrizeType.NOTHING)
                .filter(ActivityRewardPrize::hasStock)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        CommonErrorCode.VALIDATION_ERROR,
                        "Virtual farm harvest rewards are exhausted"));

        if (!reward.decrementStock()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Virtual farm harvest rewards are exhausted");
        }

        prizeRepository.save(reward);
        farm.markHarvested();
        farmRepository.save(farm);
        return new ParticipateResult(true, reward.getId(), reward.getName(), reward.getType(), reward.getValue(),
                buildAnimationHint(farm, FarmAction.HARVEST),
                "Farm harvested reward: " + reward.getName());
    }

    private void requireHarvestReward(String gameId) {
        boolean hasReward = prizeRepository.findByGameIdOrderByDisplayOrderAsc(gameId).stream()
                .anyMatch(prize -> prize.getType() != PrizeType.NOTHING);
        if (!hasReward) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Virtual farm requires at least one non-NOTHING harvest reward");
        }
    }

    private FarmConfig parseConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Virtual farm config must define max_stage, stage_progress, and water_progress");
        }
        try {
            JsonNode config = objectMapper.readTree(configJson);
            int maxStage = positiveInt(config, "max_stage", "maxStage");
            int stageProgress = positiveInt(config, "stage_progress", "stageProgress");
            int waterProgress = positiveInt(config, "water_progress", "waterProgress");
            return new FarmConfig(maxStage, stageProgress, waterProgress);
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Invalid virtual farm config", exception);
        }
    }

    private FarmAction parseAction(String payload) {
        if (payload == null || payload.isBlank()) {
            return FarmAction.WATER;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode actionNode = root.path("action");
            if (actionNode.isMissingNode() || actionNode.isNull() || actionNode.asText().isBlank()) {
                return FarmAction.WATER;
            }
            return FarmAction.valueOf(actionNode.asText().trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Unsupported virtual farm action", exception);
        } catch (IOException exception) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Invalid virtual farm payload", exception);
        }
    }

    private int positiveInt(JsonNode config, String primaryField, String fallbackField) {
        JsonNode node = config.path(primaryField);
        if (node.isMissingNode()) {
            node = config.path(fallbackField);
        }
        int value = node.asInt(0);
        if (value <= 0) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    primaryField + " must be greater than zero");
        }
        return value;
    }

    private String buildAnimationHint(ActivityVirtualFarm farm, FarmAction action) {
        ObjectNode hint = objectMapper.createObjectNode();
        hint.put("action", action.name());
        hint.put("stage", farm.getStage());
        hint.put("maxStage", farm.getMaxStage());
        hint.put("progress", farm.getProgress());
        hint.put("maxProgress", farm.getMaxProgress());
        hint.put("matured", farm.isMatured());
        hint.put("harvested", farm.isHarvested());
        if (farm.getLastWaterAt() == null) {
            hint.putNull("lastWaterAt");
        } else {
            hint.put("lastWaterAt", farm.getLastWaterAt().toString());
        }
        return hint.toString();
    }

    private record FarmConfig(int maxStage, int stageProgress, int waterProgress) {}

    private enum FarmAction {
        WATER,
        HARVEST
    }
}
