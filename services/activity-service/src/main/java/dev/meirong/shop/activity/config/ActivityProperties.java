package dev.meirong.shop.activity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.activity")
public record ActivityProperties(
    String loyaltyServiceUrl,
    AntiCheat antiCheat
) {

    public ActivityProperties {
        antiCheat = antiCheat == null ? new AntiCheat(5, 20, 10, 24, true) : antiCheat;
    }

    public record AntiCheat(
        long playerRequestsPerWindow,
        long ipRequestsPerWindow,
        long windowSeconds,
        long deviceFingerprintTtlHours,
        boolean deviceFingerprintEnabled
    ) {

        public AntiCheat {
            playerRequestsPerWindow = playerRequestsPerWindow <= 0 ? 5 : playerRequestsPerWindow;
            ipRequestsPerWindow = ipRequestsPerWindow <= 0 ? 20 : ipRequestsPerWindow;
            windowSeconds = windowSeconds <= 0 ? 10 : windowSeconds;
            deviceFingerprintTtlHours = deviceFingerprintTtlHours <= 0 ? 24 : deviceFingerprintTtlHours;
        }
    }
}
