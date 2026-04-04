package dev.meirong.shop.subscription.service;

import dev.meirong.shop.common.metrics.MetricsHelper;
import dev.meirong.shop.subscription.domain.SubscriptionEntity;
import dev.meirong.shop.subscription.domain.SubscriptionOrderLogEntity;
import dev.meirong.shop.subscription.domain.SubscriptionOrderLogRepository;
import dev.meirong.shop.subscription.domain.SubscriptionPlanEntity;
import dev.meirong.shop.subscription.domain.SubscriptionPlanRepository;
import dev.meirong.shop.subscription.domain.SubscriptionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionRenewalService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionRenewalService.class);
    private static final long RENEWAL_LOCK_LEASE_SECONDS = 1800;
    private static final String RENEWAL_LOCK = "shop:subscription:scheduler:renewal";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionOrderLogRepository orderLogRepository;
    private final RedissonClient redissonClient;
    private final MetricsHelper metrics;

    public SubscriptionRenewalService(SubscriptionRepository subscriptionRepository,
                                       SubscriptionPlanRepository planRepository,
                                       SubscriptionOrderLogRepository orderLogRepository,
                                       RedissonClient redissonClient,
                                       MeterRegistry meterRegistry) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.orderLogRepository = orderLogRepository;
        this.redissonClient = redissonClient;
        this.metrics = new MetricsHelper("subscription-service", meterRegistry);
    }

    @Scheduled(cron = "${shop.subscription.renewal-cron:0 0 2 * * ?}")
    @Transactional
    public void processRenewals() {
        Timer.Sample sample = metrics.startTimer();
        RLock lock = redissonClient.getLock(RENEWAL_LOCK);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, RENEWAL_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("Skipping subscription renewal because lock {} is held by another instance", RENEWAL_LOCK);
                metrics.increment("shop_subscription_renewal_skipped_total");
                return;
            }
            metrics.increment("shop_subscription_renewal_locked_total");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while acquiring renewal lock {}", RENEWAL_LOCK);
            return;
        }

        try {
            List<SubscriptionEntity> dueSubscriptions = subscriptionRepository
                    .findByStatusAndNextRenewalAtBefore(SubscriptionEntity.STATUS_ACTIVE, Instant.now());
            if (dueSubscriptions.isEmpty()) return;

            log.info("Processing {} subscription renewals", dueSubscriptions.size());
            int successCount = 0;
            int failureCount = 0;
            for (SubscriptionEntity sub : dueSubscriptions) {
                Timer.Sample renewSample = metrics.startTimer();
                try {
                    renewSubscription(sub);
                    successCount++;
                    metrics.recordTimer(renewSample, "shop_subscription_single_renewal_duration_seconds", "success",
                            "frequency", sub.getPlanId());
                } catch (RuntimeException exception) {
                    failureCount++;
                    log.error("Failed to renew subscription {}: {}", sub.getId(), exception.getMessage());
                    metrics.recordTimer(renewSample, "shop_subscription_single_renewal_duration_seconds", "failure",
                            "frequency", sub.getPlanId());
                }
            }
            metrics.increment("shop_subscription_renewal_processed_total",
                    "status", successCount > 0 ? "success" : "skipped");
            sample.stop(metrics.timer("shop_subscription_renewal_duration_seconds"));
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }

    private void renewSubscription(SubscriptionEntity sub) {
        SubscriptionPlanEntity plan = planRepository.findById(sub.getPlanId()).orElse(null);
        if (plan == null || !plan.isActive()) {
            log.warn("Plan {} is inactive, skipping renewal for subscription {}", sub.getPlanId(), sub.getId());
            return;
        }

        // Generate a placeholder order ID — in production, this would call order-service
        String orderId = "sub-order-" + UUID.randomUUID().toString().substring(0, 8);
        int renewalNumber = sub.getTotalRenewals() + 1;

        SubscriptionOrderLogEntity orderLog = SubscriptionOrderLogEntity.create(
                sub.getId(), orderId, renewalNumber);
        orderLogRepository.save(orderLog);

        sub.recordRenewal(orderId, plan.getFrequency());
        subscriptionRepository.save(sub);

        log.info("Renewed subscription {} (renewal #{}), orderId={}", sub.getId(), renewalNumber, orderId);
    }
}
