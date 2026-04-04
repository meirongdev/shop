package dev.meirong.shop.activity.engine;

import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.activity.domain.GameType;

import java.util.Optional;

/**
 * SPI for game plugins. Each plugin handles a specific GameType.
 * Spring auto-discovers implementations via component scanning.
 */
public interface GamePlugin {

    GameType supportedType();

    /**
     * Called when a game is activated to prepare state (e.g., preload stock).
     */
    default void initialize(ActivityGame game) {}

    /**
     * Core participation logic. Returns the result of this participation attempt.
     */
    ParticipateResult participate(ParticipateContext ctx);

    /**
     * Called when a game ends to reconcile and cleanup state.
     */
    default void settle(ActivityGame game) {}

    /**
     * Optional extension table prefix for plugin-specific data.
     */
    default Optional<String> extensionTablePrefix() { return Optional.empty(); }
}
