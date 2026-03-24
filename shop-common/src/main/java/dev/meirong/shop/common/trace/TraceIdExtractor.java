package dev.meirong.shop.common.trace;

import org.slf4j.MDC;

public final class TraceIdExtractor {

    private TraceIdExtractor() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return (traceId != null && !traceId.isBlank()) ? traceId : "";
    }
}
