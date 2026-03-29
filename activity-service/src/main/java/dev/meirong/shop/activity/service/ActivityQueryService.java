package dev.meirong.shop.activity.service;

import dev.meirong.shop.activity.domain.*;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Read-only queries for the public-facing activity endpoints.
 *
 * <p>Separates user-visible reads from admin write operations ({@link ActivityAdminService})
 * so that the {@code ActivityController} does not need to inject {@code *Repository} directly.
 */
@Service
@Transactional(readOnly = true)
public class ActivityQueryService {

    private final ActivityGameRepository gameRepository;
    private final ActivityParticipationRepository participationRepository;
    private final ActivityRewardPrizeRepository prizeRepository;

    public ActivityQueryService(ActivityGameRepository gameRepository,
                                 ActivityParticipationRepository participationRepository,
                                 ActivityRewardPrizeRepository prizeRepository) {
        this.gameRepository = gameRepository;
        this.participationRepository = participationRepository;
        this.prizeRepository = prizeRepository;
    }

    public List<ActivityGame> getActiveGames(Instant now) {
        return gameRepository.findActiveGames(now);
    }

    public ActivityGame getGame(String gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Game not found: " + gameId));
    }

    public List<ActivityRewardPrize> getPrizes(String gameId) {
        return prizeRepository.findByGameIdOrderByDisplayOrderAsc(gameId);
    }

    public List<ActivityParticipation> getParticipationHistory(String gameId, String buyerId) {
        return participationRepository.findByGameIdAndBuyerIdOrderByParticipatedAtDesc(gameId, buyerId);
    }
}
