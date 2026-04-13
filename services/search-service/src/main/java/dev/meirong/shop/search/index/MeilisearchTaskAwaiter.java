package dev.meirong.shop.search.index;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
import dev.meirong.shop.search.config.SearchProperties;
import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class MeilisearchTaskAwaiter {

    private final Client adminClient;
    private final Duration taskTimeout;
    private final Duration taskPollInterval;

    public MeilisearchTaskAwaiter(
            @Qualifier("meilisearchAdminClient") Client adminClient,
            SearchProperties properties) {
        this.adminClient = adminClient;
        this.taskTimeout = properties.meilisearch().taskTimeout();
        this.taskPollInterval = properties.meilisearch().taskPollInterval();
        Assert.isTrue(!taskTimeout.isNegative() && !taskTimeout.isZero(),
                "shop.search.meilisearch.task-timeout must be positive");
        Assert.isTrue(!taskPollInterval.isNegative() && !taskPollInterval.isZero(),
                "shop.search.meilisearch.task-poll-interval must be positive");
    }

    public void await(TaskInfo taskInfo) {
        TaskInfo requiredTaskInfo = Objects.requireNonNull(taskInfo, "taskInfo must not be null");
        long deadline = System.nanoTime() + taskTimeout.toNanos();
        while (true) {
            Task task = adminClient.getTask(requiredTaskInfo.getTaskUid());
            TaskStatus status = task.getStatus();
            if (status == TaskStatus.SUCCEEDED) {
                return;
            }
            if (status == TaskStatus.FAILED || status == TaskStatus.CANCELED) {
                throw new IllegalStateException("Meilisearch task %d finished with status %s for index '%s': %s"
                        .formatted(task.getUid(), status, task.getIndexUid(), task.getError()));
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Timed out after %s waiting for Meilisearch task %d on index '%s'"
                        .formatted(taskTimeout, task.getUid(), task.getIndexUid()));
            }
            try {
                Thread.sleep(taskPollInterval.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while waiting for Meilisearch task %d".formatted(task.getUid()), exception);
            }
        }
    }
}
