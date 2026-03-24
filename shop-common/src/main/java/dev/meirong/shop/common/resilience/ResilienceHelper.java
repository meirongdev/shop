package dev.meirong.shop.common.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

public class ResilienceHelper implements AutoCloseable {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final BulkheadRegistry bulkheadRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public ResilienceHelper(CircuitBreakerRegistry circuitBreakerRegistry,
                            RetryRegistry retryRegistry,
                            BulkheadRegistry bulkheadRegistry,
                            TimeLimiterRegistry timeLimiterRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
    }

    public <T> T read(String instanceName, Supplier<T> supplier) {
        return execute(instanceName, true, supplier);
    }

    public <T> T read(String instanceName, Supplier<T> supplier, Function<Throwable, T> fallback) {
        return execute(instanceName, true, supplier, fallback);
    }

    public <T> T write(String instanceName, Supplier<T> supplier) {
        return execute(instanceName, false, supplier);
    }

    public <T> T write(String instanceName, Supplier<T> supplier, Function<Throwable, T> fallback) {
        return execute(instanceName, false, supplier, fallback);
    }

    public void write(String instanceName, Runnable runnable) {
        write(instanceName, () -> {
            runnable.run();
            return null;
        });
    }

    public void write(String instanceName, Runnable runnable, Function<Throwable, Void> fallback) {
        write(instanceName, () -> {
            runnable.run();
            return null;
        }, fallback);
    }

    private <T> T execute(String instanceName, boolean retryEnabled, Supplier<T> supplier) {
        return execute(instanceName, retryEnabled, supplier, this::rethrow);
    }

    private <T> T execute(String instanceName,
                          boolean retryEnabled,
                          Supplier<T> supplier,
                          Function<Throwable, T> fallback) {
        try {
            Supplier<T> retryDecorated = supplier;
            if (retryEnabled) {
                Retry retry = retryRegistry.retry(instanceName);
                retryDecorated = Retry.decorateSupplier(retry, retryDecorated);
            }
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(instanceName);
            Supplier<T> circuitDecorated = CircuitBreaker.decorateSupplier(circuitBreaker, retryDecorated);

            Bulkhead bulkhead = bulkheadRegistry.bulkhead(instanceName);
            Supplier<T> bulkheadDecorated = Bulkhead.decorateSupplier(bulkhead, circuitDecorated);

            TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(instanceName);
            return timeLimiter.executeFutureSupplier(() -> executorService.submit(bulkheadDecorated::get));
        } catch (Throwable throwable) {
            return fallback.apply(unwrap(throwable));
        }
    }

    private <T> T rethrow(Throwable throwable) {
        if (throwable instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof TimeoutException timeoutException) {
            throw new IllegalStateException(timeoutException.getMessage(), timeoutException);
        }
        throw new IllegalStateException(throwable.getMessage(), throwable);
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return throwable;
        }
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return unwrap(completionException.getCause());
        }
        if (throwable.getCause() != null
                && (throwable instanceof java.util.concurrent.ExecutionException
                || throwable instanceof IllegalStateException)) {
            return unwrap(throwable.getCause());
        }
        return throwable;
    }

    @Override
    public void close() {
        executorService.close();
    }
}
