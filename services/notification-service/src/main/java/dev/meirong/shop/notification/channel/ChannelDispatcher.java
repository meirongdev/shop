package dev.meirong.shop.notification.channel;

import dev.meirong.shop.common.metrics.MetricsHelper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ChannelDispatcher {

    private final Map<String, NotificationChannel> channels;
    private final MetricsHelper metrics;

    public ChannelDispatcher(List<NotificationChannel> channelList, MeterRegistry meterRegistry) {
        this.channels = channelList.stream()
                .collect(Collectors.toMap(NotificationChannel::channelType, Function.identity()));
        this.metrics = new MetricsHelper("notification-service", meterRegistry);
    }

    public void dispatch(String channelType, NotificationRequest request) {
        Timer.Sample sample = metrics.startTimer();
        String result = "success";
        try {
            NotificationChannel channel = channels.get(channelType);
            if (channel == null) {
                throw new IllegalArgumentException("Unknown notification channel: " + channelType);
            }
            channel.send(request);
        } catch (RuntimeException e) {
            result = "failed";
            throw e;
        } finally {
            metrics.increment("shop_notification_dispatched_total",
                    "channel", channelType, "result", result);
            sample.stop(metrics.timer("shop_notification_dispatch_duration_seconds",
                    "channel", channelType, "result", result));
        }
    }
}
