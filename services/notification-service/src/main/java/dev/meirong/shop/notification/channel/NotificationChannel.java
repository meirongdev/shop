package dev.meirong.shop.notification.channel;

public interface NotificationChannel {

    String channelType();

    void send(NotificationRequest request);
}
