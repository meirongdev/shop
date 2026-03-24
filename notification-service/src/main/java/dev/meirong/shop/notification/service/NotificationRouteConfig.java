package dev.meirong.shop.notification.service;

import java.util.Map;

public record NotificationRouteConfig(String templateCode, String channel, String subject) {

    private static final Map<String, NotificationRouteConfig> ROUTES = Map.of(
            "USER_REGISTERED",      new NotificationRouteConfig("welcome-email",     "EMAIL", "Welcome to Shop!"),
            "ORDER_CONFIRMED",      new NotificationRouteConfig("order-confirmed",   "EMAIL", "Order Confirmed"),
            "ORDER_SHIPPED",        new NotificationRouteConfig("order-shipped",     "EMAIL", "Your Order Has Shipped"),
            "ORDER_COMPLETED",      new NotificationRouteConfig("order-completed",   "EMAIL", "Order Completed"),
            "ORDER_CANCELLED",      new NotificationRouteConfig("order-cancelled",   "EMAIL", "Order Cancelled"),
            "DEPOSIT_COMPLETED",    new NotificationRouteConfig("wallet-deposit",    "EMAIL", "Deposit Received"),
            "WITHDRAWAL_COMPLETED", new NotificationRouteConfig("wallet-withdrawal", "EMAIL", "Withdrawal Processed")
    );

    public static NotificationRouteConfig resolve(String eventType) {
        return ROUTES.get(eventType);
    }
}
