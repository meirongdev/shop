package dev.meirong.shop.activity.engine;

import dev.meirong.shop.activity.domain.GameType;

import java.time.Instant;

public record ParticipateContext(
    String gameId,
    GameType gameType,
    String buyerId,
    String sessionId,
    String gameConfig,
    String payload,
    Instant requestTime
) {}
