package dev.meirong.shop.marketplace.service;

import dev.meirong.shop.marketplace.domain.CompensationTaskEntity;
import dev.meirong.shop.marketplace.domain.CompensationTaskRepository;
import dev.meirong.shop.contracts.marketplace.MarketplaceApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Retries PENDING marketplace compensation tasks with exponential backoff.
 * Task types: ROLLBACK_INVENTORY (restore stock after failed order), CANCEL_RESERVATION.
 */
@Component
public class CompensationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(CompensationRetryScheduler.class);

    private final CompensationTaskRepository taskRepository;
    private final MarketplaceApplicationService marketplaceService;

    public CompensationRetryScheduler(CompensationTaskRepository taskRepository,
                                      MarketplaceApplicationService marketplaceService) {
        this.taskRepository = taskRepository;
        this.marketplaceService = marketplaceService;
    }

    @Scheduled(fixedDelayString = "${shop.compensation.retry-interval-ms:60000}")
    @Transactional
    public void retryPendingTasks() {
        List<CompensationTaskEntity> dueTasks = taskRepository.findDuePendingTasks(Instant.now());
        if (dueTasks.isEmpty()) return;

        log.info("Marketplace compensation retry: found {} due task(s)", dueTasks.size());
        for (CompensationTaskEntity task : dueTasks) {
            try {
                executeTask(task);
                task.markSucceeded();
                log.info("Marketplace compensation task succeeded: id={} type={}", task.getId(), task.getTaskType());
            } catch (Exception e) {
                task.recordFailure(e.getMessage());
                if (task.getStatus() == CompensationTaskEntity.Status.FAILED) {
                    log.error("Marketplace compensation task permanently failed: id={} type={} error={}",
                            task.getId(), task.getTaskType(), e.getMessage());
                } else {
                    log.warn("Marketplace compensation retry {}/{}: id={} type={} nextRetry={}",
                            task.getRetryCount(), task.getMaxRetries(),
                            task.getId(), task.getTaskType(), task.getNextRetryAt());
                }
            }
            taskRepository.save(task);
        }
    }

    private void executeTask(CompensationTaskEntity task) {
        switch (task.getTaskType()) {
            case "ROLLBACK_INVENTORY" -> {
                // aggregateId = productId, payload = quantity as string
                int quantity = Integer.parseInt(task.getPayload().trim());
                marketplaceService.restoreInventory(
                        new MarketplaceApi.RestoreInventoryRequest(task.getAggregateId(), quantity));
            }
            default -> throw new IllegalArgumentException(
                    "Unknown marketplace compensation task type: " + task.getTaskType());
        }
    }
}
