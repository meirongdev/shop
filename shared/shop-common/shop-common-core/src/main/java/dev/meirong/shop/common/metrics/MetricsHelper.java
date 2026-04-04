package dev.meirong.shop.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Helper for creating consistent metrics across services.
 * All metrics use the {@code shop_} prefix and follow naming conventions:
 * <ul>
 *   <li>Counters: {@code shop_<domain>_<action>_total}</li>
 *   <li>Timers: {@code shop_<domain>_<action>_duration_seconds}</li>
 * </ul>
 */
public final class MetricsHelper {

    private final String serviceName;
    private final MeterRegistry meterRegistry;

    public MetricsHelper(String serviceName, MeterRegistry meterRegistry) {
        this.serviceName = serviceName;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Create a counter with service tag.
     */
    public Counter counter(String name, String... extraTags) {
        Counter.Builder builder = Counter.builder(name).tag("service", serviceName);
        for (int i = 0; i < extraTags.length; i += 2) {
            builder.tag(extraTags[i], extraTags[i + 1]);
        }
        return builder.register(meterRegistry);
    }

    /**
     * Create a timer with service tag.
     */
    public Timer timer(String name, String... extraTags) {
        Timer.Builder builder = Timer.builder(name).tag("service", serviceName);
        for (int i = 0; i < extraTags.length; i += 2) {
            builder.tag(extraTags[i], extraTags[i + 1]);
        }
        return builder.register(meterRegistry);
    }

    /**
     * Start a timer sample for ad-hoc duration measurement.
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Convenience: increment a counter by name with tags.
     */
    public void increment(String counterName, String... tags) {
        counter(counterName, tags).increment();
    }

    /**
     * Convenience: record timer duration with result tag.
     */
    public void recordTimer(Timer.Sample sample, String timerName, String result, String... extraTags) {
        Timer timer = timer(timerName, extraTags);
        sample.stop(timer);
    }
}
