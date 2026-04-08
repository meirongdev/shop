package dev.meirong.shop.activity.service;

import dev.meirong.shop.activity.domain.ActivityParticipation;
import dev.meirong.shop.activity.domain.ActivityParticipationRepository;
import dev.meirong.shop.common.metrics.MetricsHelper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatches rewards for winning participations.
 * Currently logs reward dispatch; actual HTTP calls to loyalty/promotion/wallet
 * would be integrated when those services expose internal endpoints.
 */
@Service
public class RewardDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RewardDispatcher.class);

    private final ActivityParticipationRepository participationRepository;
    private final MetricsHelper metricsHelper;

    public RewardDispatcher(ActivityParticipationRepository participationRepository,
                            MetricsHelper metricsHelper) {
        this.participationRepository = participationRepository;
        this.metricsHelper = metricsHelper;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void compensatePendingRewards() {
        Instant threshold = Instant.now().minus(2, ChronoUnit.MINUTES);
        List<ActivityParticipation> pending = participationRepository.findPendingRewards(threshold);

        metricsHelper.gauge("shop_activity_reward_pending_count", pending.size());

        for (ActivityParticipation p : pending) {
            try {
                dispatch(p);
                p.markDispatched(p.getId());
                participationRepository.save(p);
                log.info("Dispatched reward: participation={}, player={}", p.getId(), p.getBuyerId());
                metricsHelper.increment("shop_activity_reward_dispatch_total", "result", "success");
            } catch (RuntimeException exception) {
                p.markFailed();
                participationRepository.save(p);
                log.error("Failed to dispatch reward: participation={}", p.getId(), exception);
                metricsHelper.increment("shop_activity_reward_dispatch_total", "result", "failure");
            }
        }
    }

    public void dispatch(ActivityParticipation p) {
        // Reward dispatch stub — integrates with loyalty/promotion/wallet via HTTP
        log.info("Dispatching reward for participation {}: prize={}, type={}",
                p.getId(), p.getPrizeId(), p.getPrizeSnapshot());
    }
}
