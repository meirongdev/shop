package dev.meirong.shop.activity.service;

import dev.meirong.shop.activity.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Dispatches rewards for winning participations.
 * Currently logs reward dispatch; actual HTTP calls to loyalty/promotion/wallet
 * would be integrated when those services expose internal endpoints.
 */
@Service
public class RewardDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RewardDispatcher.class);

    private final ActivityParticipationRepository participationRepository;

    public RewardDispatcher(ActivityParticipationRepository participationRepository) {
        this.participationRepository = participationRepository;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void compensatePendingRewards() {
        Instant threshold = Instant.now().minus(2, ChronoUnit.MINUTES);
        List<ActivityParticipation> pending = participationRepository.findPendingRewards(threshold);

        for (ActivityParticipation p : pending) {
            try {
                dispatch(p);
                p.markDispatched(p.getId());
                participationRepository.save(p);
                log.info("Dispatched reward: participation={}, player={}", p.getId(), p.getBuyerId());
            } catch (RuntimeException exception) {
                p.markFailed();
                participationRepository.save(p);
                log.error("Failed to dispatch reward: participation={}", p.getId(), exception);
            }
        }
    }

    public void dispatch(ActivityParticipation p) {
        // Reward dispatch stub — integrates with loyalty/promotion/wallet via HTTP
        log.info("Dispatching reward for participation {}: prize={}, type={}",
                p.getId(), p.getPrizeId(), p.getPrizeSnapshot());
    }
}
