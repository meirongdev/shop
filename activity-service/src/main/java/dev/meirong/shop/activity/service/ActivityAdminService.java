package dev.meirong.shop.activity.service;

import dev.meirong.shop.activity.domain.*;
import dev.meirong.shop.activity.engine.GamePluginRegistry;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ActivityAdminService {

    private static final Logger log = LoggerFactory.getLogger(ActivityAdminService.class);

    private final ActivityGameRepository gameRepository;
    private final ActivityRewardPrizeRepository prizeRepository;
    private final GamePluginRegistry pluginRegistry;

    public ActivityAdminService(ActivityGameRepository gameRepository,
                                 ActivityRewardPrizeRepository prizeRepository,
                                 GamePluginRegistry pluginRegistry) {
        this.gameRepository = gameRepository;
        this.prizeRepository = prizeRepository;
        this.pluginRegistry = pluginRegistry;
    }

    @Transactional
    public ActivityGame createGame(GameType type, String name, String config,
                                    int dailyLimit, int totalLimit, String createdBy) {
        String id = UUID.randomUUID().toString();
        ActivityGame game = new ActivityGame(id, type, name);
        game.setConfig(config);
        game.setPerUserDailyLimit(dailyLimit);
        game.setPerUserTotalLimit(totalLimit);
        game.setCreatedBy(createdBy);
        return gameRepository.save(game);
    }

    @Transactional
    public ActivityGame updateGame(String gameId, String name, String config,
                                    int dailyLimit, int totalLimit) {
        ActivityGame game = gameRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Game not found"));
        if (game.getStatus() != GameStatus.DRAFT && game.getStatus() != GameStatus.SCHEDULED) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Can only update DRAFT or SCHEDULED games");
        }
        game.setName(name);
        game.setConfig(config);
        game.setPerUserDailyLimit(dailyLimit);
        game.setPerUserTotalLimit(totalLimit);
        return gameRepository.save(game);
    }

    @Transactional
    public ActivityGame activateGame(String gameId) {
        ActivityGame game = gameRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Game not found"));
        game.activate();
        pluginRegistry.requirePlugin(game.getType()).initialize(game);
        log.info("Game activated: id={}, type={}, name={}", game.getId(), game.getType(), game.getName());
        return gameRepository.save(game);
    }

    @Transactional
    public ActivityGame endGame(String gameId) {
        ActivityGame game = gameRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Game not found"));
        game.end();
        pluginRegistry.requirePlugin(game.getType()).settle(game);
        log.info("Game ended: id={}, type={}, name={}", game.getId(), game.getType(), game.getName());
        return gameRepository.save(game);
    }

    @Transactional
    public ActivityRewardPrize addPrize(String gameId, ActivityRewardPrize prize) {
        gameRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Game not found"));
        return prizeRepository.save(prize);
    }

    public List<ActivityRewardPrize> getPrizes(String gameId) {
        return prizeRepository.findByGameIdOrderByDisplayOrderAsc(gameId);
    }

    public List<ActivityGame> getActiveGames(Instant now) {
        return gameRepository.findActiveGames(now);
    }
}
