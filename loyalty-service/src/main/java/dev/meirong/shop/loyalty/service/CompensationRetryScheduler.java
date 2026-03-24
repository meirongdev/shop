package dev.meirong.shop.loyalty.service;

import dev.meirong.shop.loyalty.domain.CompensationTaskEntity;
import dev.meirong.shop.loyalty.domain.CompensationTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Retries PENDING loyalty compensation tasks with exponential backoff.
 * Task types: ROLLBACK_POINTS (reverse incorrectly awarded points on order reversal).
 */
@Component
public class CompensationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(CompensationRetryScheduler.class);

    private final CompensationTaskRepository taskRepository;
    private final LoyaltyAccountService loyaltyAccountService;

    public CompensationRetryScheduler(CompensationTaskRepository taskRepository,
                                      LoyaltyAccountService loyaltyAccountService) {
        this.taskRepository = taskRepository;
        this.loyaltyAccountService = loyaltyAccountService;
    }

    @Scheduled(fixedDelayString = "${shop.compensation.retry-interval-ms:60000}")
    @Transactional
    public void retryPendingTasks() {
        List<CompensationTaskEntity> dueTasks = taskRepository.findDuePendingTasks(Instant.now());
        if (dueTasks.isEmpty()) return;

        log.info("Loyalty compensation retry: found {} due task(s)", dueTasks.size());
        for (CompensationTaskEntity task : dueTasks) {
            try {
                executeTask(task);
                task.markSucceeded();
                log.info("Loyalty compensation task succeeded: id={} type={}", task.getId(), task.getTaskType());
            } catch (Exception e) {
                task.recordFailure(e.getMessage());
                if (task.getStatus() == CompensationTaskEntity.Status.FAILED) {
                    log.error("Loyalty compensation task permanently failed: id={} type={} error={}",
                            task.getId(), task.getTaskType(), e.getMessage());
                } else {
                    log.warn("Loyalty compensation retry {}/{}: id={} type={} nextRetry={}",
                            task.getRetryCount(), task.getMaxRetries(),
                            task.getId(), task.getTaskType(), task.getNextRetryAt());
                }
            }
            taskRepository.save(task);
        }
    }

    private void executeTask(CompensationTaskEntity task) {
        switch (task.getTaskType()) {
            case "ROLLBACK_POINTS" -> {
                // aggregateId = buyerId, payload = "points:referenceId"
                String[] parts = task.getPayload().split(":", 2);
                long points = Long.parseLong(parts[0]);
                String referenceId = parts.length > 1 ? parts[1] : task.getId();
                loyaltyAccountService.deductPoints(
                        task.getAggregateId(), "COMPENSATION",
                        points, referenceId, "Compensation rollback for " + referenceId);
            }
            default -> throw new IllegalArgumentException(
                    "Unknown loyalty compensation task type: " + task.getTaskType());
        }
    }
}
