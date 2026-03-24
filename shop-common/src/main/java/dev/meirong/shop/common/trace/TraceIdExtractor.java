package dev.meirong.shop.common.trace;

import java.util.UUID;
import org.slf4j.MDC;

public final class TraceIdExtractor {

    private TraceIdExtractor() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return UUID.randomUUID().toString();
    }
}
