package dev.meirong.shop.activity.service;

import dev.meirong.shop.activity.domain.*;
import dev.meirong.shop.activity.engine.GamePluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class GameScheduler {

    private static final Logger log = LoggerFactory.getLogger(GameScheduler.class);

    private final ActivityGameRepository gameRepository;
    private final GamePluginRegistry pluginRegistry;

    public GameScheduler(ActivityGameRepository gameRepository, GamePluginRegistry pluginRegistry) {
        this.gameRepository = gameRepository;
        this.pluginRegistry = pluginRegistry;
    }

    /** Auto-activate scheduled games when their start time arrives. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void activateScheduledGames() {
        Instant now = Instant.now();
        List<ActivityGame> readyGames = gameRepository.findScheduledGamesReadyToActivate(now);
        for (ActivityGame game : readyGames) {
            try {
                game.activate();
                pluginRegistry.requirePlugin(game.getType()).initialize(game);
                gameRepository.save(game);
                log.info("Auto-activated game: id={}, name={}", game.getId(), game.getName());
            } catch (RuntimeException exception) {
                log.error("Failed to auto-activate game id={}", game.getId(), exception);
            }
        }
    }

    /** Auto-end active games whose end time has passed. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void endExpiredGames() {
        Instant now = Instant.now();
        List<ActivityGame> expired = gameRepository.findExpiredActiveGames(now);
        for (ActivityGame game : expired) {
            try {
                game.end();
                pluginRegistry.requirePlugin(game.getType()).settle(game);
                gameRepository.save(game);
                log.info("Auto-ended game: id={}, name={}", game.getId(), game.getName());
            } catch (RuntimeException exception) {
                log.error("Failed to auto-end game id={}", game.getId(), exception);
            }
        }
    }
}
