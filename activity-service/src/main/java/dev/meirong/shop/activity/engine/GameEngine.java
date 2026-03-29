package dev.meirong.shop.activity.engine;

import dev.meirong.shop.activity.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.meirong.shop.activity.service.AntiCheatGuard;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class GameEngine {

    private static final Logger log = LoggerFactory.getLogger(GameEngine.class);

    private final ActivityGameRepository gameRepository;
    private final ActivityParticipationRepository participationRepository;
    private final GamePluginRegistry pluginRegistry;
    private final AntiCheatGuard antiCheatGuard;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public GameEngine(ActivityGameRepository gameRepository,
                      ActivityParticipationRepository participationRepository,
                      GamePluginRegistry pluginRegistry,
                      AntiCheatGuard antiCheatGuard,
                      ObjectMapper objectMapper,
                      MeterRegistry meterRegistry) {
        this.gameRepository = gameRepository;
        this.participationRepository = participationRepository;
        this.pluginRegistry = pluginRegistry;
        this.antiCheatGuard = antiCheatGuard;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public ParticipateResult participate(String gameId, String buyerId, String payload,
                                          String ipAddress, String deviceFingerprint) {
        ActivityGame game = gameRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Game not found"));

        if (!game.isActive()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Game is not active");
        }

        antiCheatGuard.check(game, buyerId, ipAddress, deviceFingerprint);
        validateLimits(game, buyerId);

        GamePlugin plugin = pluginRegistry.requirePlugin(game.getType());

        ParticipateContext ctx = new ParticipateContext(
                gameId, game.getType(), buyerId, null,
                game.getConfig(), payload, Instant.now()
        );

        ParticipateResult result = plugin.participate(ctx);

        ActivityParticipation participation = new ActivityParticipation(
                UUID.randomUUID().toString(), gameId, game.getType(), buyerId);
        participation.setIpAddress(ipAddress);
        participation.setDeviceFingerprint(deviceFingerprint);

        if (result.win()) {
            participation.markWin(result.prizeId(), buildPrizeSnapshot(result));
            if (shouldSkipRewardDispatch(result)) {
                participation.markRewardSkipped();
            }
            Counter.builder("shop_activity_wins_total")
                    .description("Total activity wins")
                    .tag("game_type", game.getType().name())
                    .register(meterRegistry).increment();
        } else {
            participation.markMiss();
        }

        participationRepository.save(participation);
        game.incrementParticipantCount();
        gameRepository.save(game);

        Counter.builder("shop_activity_participations_total")
                .description("Total activity participations")
                .tag("game_type", game.getType().name())
                .register(meterRegistry).increment();

        log.info("Player {} participated in game {}, result: {}", buyerId, gameId, result.win() ? "WIN" : "MISS");
        return result;
    }

    private void validateLimits(ActivityGame game, String buyerId) {
        if (buyerId == null) return;

        long totalCount = participationRepository.countByGameIdAndBuyerId(game.getId(), buyerId);
        if (game.getPerUserTotalLimit() > 0 && totalCount >= game.getPerUserTotalLimit()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "You have reached the total participation limit for this game");
        }

        Instant todayStart = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();
        long dailyCount = participationRepository.countByGameIdAndBuyerIdSince(game.getId(), buyerId, todayStart);
        if (game.getPerUserDailyLimit() > 0 && dailyCount >= game.getPerUserDailyLimit()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "You have reached the daily participation limit for this game");
        }
    }

    private String buildPrizeSnapshot(ParticipateResult result) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        if (result.prizeId() == null) {
            snapshot.putNull("prizeId");
        } else {
            snapshot.put("prizeId", result.prizeId());
        }
        if (result.prizeName() == null) {
            snapshot.putNull("prizeName");
        } else {
            snapshot.put("prizeName", result.prizeName());
        }
        if (result.prizeType() == null) {
            snapshot.putNull("prizeType");
        } else {
            snapshot.put("prizeType", result.prizeType().name());
        }
        if (result.prizeValue() == null) {
            snapshot.putNull("prizeValue");
        } else {
            snapshot.put("prizeValue", result.prizeValue());
        }
        if (result.animationHint() == null) {
            snapshot.putNull("animationHint");
        } else {
            snapshot.put("animationHint", result.animationHint());
        }
        return snapshot.toString();
    }

    private boolean shouldSkipRewardDispatch(ParticipateResult result) {
        return result.prizeType() == PrizeType.CARD || result.prizeType() == PrizeType.PROGRESS;
    }
}
