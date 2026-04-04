package dev.meirong.shop.notification.channel;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ChannelDispatcher {

    private final Map<String, NotificationChannel> channels;

    public ChannelDispatcher(List<NotificationChannel> channelList) {
        this.channels = channelList.stream()
                .collect(Collectors.toMap(NotificationChannel::channelType, Function.identity()));
    }

    public void dispatch(String channelType, NotificationRequest request) {
        NotificationChannel channel = channels.get(channelType);
        if (channel == null) {
            throw new IllegalArgumentException("Unknown notification channel: " + channelType);
        }
        channel.send(request);
    }
}
