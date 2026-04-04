package dev.meirong.shop.order.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderScheduler.class);
    private static final long SCHEDULER_LOCK_LEASE_SECONDS = 300;
    private static final String CANCEL_EXPIRED_LOCK = "shop:order:scheduler:cancel-expired";
    private static final String AUTO_COMPLETE_LOCK = "shop:order:scheduler:auto-complete";

    private final OrderApplicationService orderService;
    private final RedissonClient redissonClient;

    public OrderScheduler(OrderApplicationService orderService, RedissonClient redissonClient) {
        this.orderService = orderService;
        this.redissonClient = redissonClient;
    }

    /** Cancel unpaid orders older than 30 minutes */
    @Scheduled(fixedDelayString = "${shop.order.cancel-check-delay-ms:60000}")
    public void cancelExpiredOrders() {
        Instant threshold = Instant.now().minus(30, ChronoUnit.MINUTES);
        runWithSchedulerLock(CANCEL_EXPIRED_LOCK, "cancelExpiredOrders",
                () -> orderService.cancelExpiredOrders(threshold));
    }

    /** Auto-complete delivered orders after 7 days */
    @Scheduled(fixedDelayString = "${shop.order.auto-complete-delay-ms:3600000}")
    public void autoCompleteDeliveredOrders() {
        Instant threshold = Instant.now().minus(7, ChronoUnit.DAYS);
        runWithSchedulerLock(AUTO_COMPLETE_LOCK, "autoCompleteDeliveredOrders",
                () -> orderService.autoCompleteDeliveredOrders(threshold));
    }

    private void runWithSchedulerLock(String lockName, String taskName, Runnable task) {
        RLock lock = redissonClient.getLock(lockName);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, SCHEDULER_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("Skipping {} because lock {} is held by another instance", taskName, lockName);
                return;
            }
            task.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while acquiring scheduler lock {} for {}", lockName, taskName);
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }
}
