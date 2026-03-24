package dev.meirong.shop.promotion.service;

import dev.meirong.shop.promotion.domain.CompensationTaskEntity;
import dev.meirong.shop.promotion.domain.CompensationTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Retries PENDING compensation tasks with exponential backoff.
 * A task represents a compensatable side-effect (e.g. coupon void, offer rollback)
 * that must eventually succeed or be flagged as FAILED for manual intervention.
 */
@Component
public class CompensationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(CompensationRetryScheduler.class);

    private final CompensationTaskRepository taskRepository;
    private final PromotionApplicationService promotionService;

    public CompensationRetryScheduler(CompensationTaskRepository taskRepository,
                                      PromotionApplicationService promotionService) {
        this.taskRepository = taskRepository;
        this.promotionService = promotionService;
    }

    @Scheduled(fixedDelayString = "${shop.compensation.retry-interval-ms:60000}")
    @Transactional
    public void retryPendingTasks() {
        List<CompensationTaskEntity> dueTasks = taskRepository.findDuePendingTasks(Instant.now());
        if (dueTasks.isEmpty()) return;

        log.info("Compensation retry: found {} due task(s)", dueTasks.size());
        for (CompensationTaskEntity task : dueTasks) {
            try {
                executeTask(task);
                task.markSucceeded();
                log.info("Compensation task succeeded: id={} type={}", task.getId(), task.getTaskType());
            } catch (Exception e) {
                task.recordFailure(e.getMessage());
                if (task.getStatus() == CompensationTaskEntity.Status.FAILED) {
                    log.error("Compensation task permanently failed after {} retries: id={} type={} error={}",
                            task.getRetryCount(), task.getId(), task.getTaskType(), e.getMessage());
                } else {
                    log.warn("Compensation task retry {}/{} failed: id={} type={} nextRetry={}",
                            task.getRetryCount(), task.getMaxRetries(),
                            task.getId(), task.getTaskType(), task.getNextRetryAt());
                }
            }
            taskRepository.save(task);
        }
    }

    private void executeTask(CompensationTaskEntity task) {
        switch (task.getTaskType()) {
            case "VOID_COUPON" -> promotionService.voidCoupon(task.getAggregateId());
            case "ROLLBACK_OFFER" -> promotionService.rollbackOffer(task.getAggregateId());
            default -> throw new IllegalArgumentException("Unknown compensation task type: " + task.getTaskType());
        }
    }
}
