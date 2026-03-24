package dev.meirong.shop.common.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.security.internal")
public record InternalSecurityProperties(boolean enabled, String token, List<String> excludedPathPrefixes) {

    public InternalSecurityProperties {
        excludedPathPrefixes = excludedPathPrefixes == null || excludedPathPrefixes.isEmpty()
                ? List.of("/actuator")
                : List.copyOf(excludedPathPrefixes);
    }
}
