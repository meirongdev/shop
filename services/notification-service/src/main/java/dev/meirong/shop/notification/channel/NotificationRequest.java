package dev.meirong.shop.notification.channel;

import java.util.Map;

public record NotificationRequest(
        String recipientAddr,
        String templateCode,
        String subject,
        Map<String, Object> variables
) {
}
