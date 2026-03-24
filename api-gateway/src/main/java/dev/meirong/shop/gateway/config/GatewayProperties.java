package dev.meirong.shop.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.gateway")
public record GatewayProperties(String jwtSecret,
                                String internalToken,
                                RateLimit rateLimit,
                                Cors cors) {

    public GatewayProperties {
        rateLimit = rateLimit == null ? new RateLimit(100, 20) : rateLimit;
        cors = cors == null ? new Cors(null, null, null, null, true, 3600) : cors;
    }

    public record RateLimit(long requestsPerMinute, long burst) {

        public RateLimit {
            requestsPerMinute = requestsPerMinute <= 0 ? 100 : requestsPerMinute;
            burst = burst <= 0 ? 20 : burst;
        }
    }

    public record Cors(List<String> allowedOriginPatterns,
                       List<String> allowedMethods,
                       List<String> allowedHeaders,
                       List<String> exposedHeaders,
                       boolean allowCredentials,
                       long maxAgeSeconds) {

        public Cors {
            allowedOriginPatterns = allowedOriginPatterns == null || allowedOriginPatterns.isEmpty()
                    ? List.of("http://localhost:*", "http://127.0.0.1:*", "http://[::1]:*")
                    : List.copyOf(allowedOriginPatterns);
            allowedMethods = allowedMethods == null || allowedMethods.isEmpty()
                    ? List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    : List.copyOf(allowedMethods);
            allowedHeaders = allowedHeaders == null || allowedHeaders.isEmpty()
                    ? List.of("Authorization", "Content-Type", "X-Request-Id", "X-Device-Fingerprint")
                    : List.copyOf(allowedHeaders);
            exposedHeaders = exposedHeaders == null ? List.of("X-Request-Id") : List.copyOf(exposedHeaders);
            maxAgeSeconds = maxAgeSeconds <= 0 ? 3600 : maxAgeSeconds;
        }
    }
}
