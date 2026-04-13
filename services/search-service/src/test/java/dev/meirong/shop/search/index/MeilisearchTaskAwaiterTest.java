package dev.meirong.shop.search.index;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.model.Task;
import com.meilisearch.sdk.model.TaskError;
import com.meilisearch.sdk.model.TaskInfo;
import com.meilisearch.sdk.model.TaskStatus;
import dev.meirong.shop.search.config.SearchProperties;
import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeilisearchTaskAwaiterTest {

    @Mock
    private Client adminClient;

    @Test
    void await_returnsWhenTaskSucceeds() throws Exception {
        TaskInfo taskInfo = taskInfo(101);
        when(adminClient.getTask(101)).thenReturn(task(TaskStatus.SUCCEEDED, 101, "products", null));

        MeilisearchTaskAwaiter awaiter = new MeilisearchTaskAwaiter(adminClient, properties(Duration.ofSeconds(1)));

        awaiter.await(taskInfo);
    }

    @Test
    void await_throwsWhenTaskFails() throws Exception {
        TaskInfo taskInfo = taskInfo(202);
        TaskError error = new TaskError();
        setField(TaskError.class, error, "message", "boom");
        when(adminClient.getTask(202)).thenReturn(task(TaskStatus.FAILED, 202, "products", error));

        MeilisearchTaskAwaiter awaiter = new MeilisearchTaskAwaiter(adminClient, properties(Duration.ofSeconds(1)));

        assertThatThrownBy(() -> awaiter.await(taskInfo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Meilisearch task 202 finished with status failed");
    }

    @Test
    void await_throwsWhenTaskTimesOut() throws Exception {
        TaskInfo taskInfo = taskInfo(303);
        when(adminClient.getTask(303)).thenReturn(task(TaskStatus.PROCESSING, 303, "products", null));

        MeilisearchTaskAwaiter awaiter = new MeilisearchTaskAwaiter(adminClient, properties(Duration.ofMillis(150)));

        assertThatThrownBy(() -> awaiter.await(taskInfo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timed out after");
    }

    private static SearchProperties properties(Duration timeout) {
        return new SearchProperties(
                new SearchProperties.MeilisearchProperties(
                        "http://localhost:7700",
                        "admin",
                        "search",
                        timeout,
                        Duration.ofMillis(50)),
                "marketplace.product.events.v1",
                "http://marketplace-service:8080",
                new SearchProperties.AnalyticsProperties(Duration.ofDays(7), 5000, 2));
    }

    private static TaskInfo taskInfo(int taskUid) throws Exception {
        TaskInfo taskInfo = new TaskInfo();
        setField(TaskInfo.class, taskInfo, "taskUid", taskUid);
        return taskInfo;
    }

    private static Task task(TaskStatus status, int uid, String indexUid, TaskError error) throws Exception {
        Task task = new Task();
        setField(Task.class, task, "status", status);
        setField(Task.class, task, "uid", uid);
        setField(Task.class, task, "indexUid", indexUid);
        setField(Task.class, task, "error", error);
        return task;
    }

    private static void setField(Class<?> type, Object target, String fieldName, Object value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
