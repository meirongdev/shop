package dev.meirong.shop.common.trace;

import org.slf4j.MDC;

public final class TraceIdExtractor {

    private TraceIdExtractor() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return (traceId != null && !traceId.isBlank()) ? traceId : "";
    }

    public static String currentRequestId() {
        String requestId = MDC.get("requestId");
        return (requestId != null && !requestId.isBlank()) ? requestId : "";
    }
}
