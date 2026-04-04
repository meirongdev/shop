package dev.meirong.shop.common.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.profiling")
public record ProfilingProperties(boolean enabled, String serverAddress) {

    public ProfilingProperties {
        serverAddress = (serverAddress == null || serverAddress.isBlank())
                ? "http://pyroscope:4040"
                : serverAddress;
    }
}
