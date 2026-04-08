package dev.meirong.shop.common.web;

import dev.meirong.shop.common.error.BusinessErrorCode;
import dev.meirong.shop.common.trace.TraceIdExtractor;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;

public final class ShopProblemDetails {

    private static final String SERVICE_NAME = System.getProperty("spring.application.name",
            System.getenv("SPRING_APPLICATION_NAME") != null ? System.getenv("SPRING_APPLICATION_NAME") : "unknown-service");

    private ShopProblemDetails() {
    }

    public static ProblemDetail from(BusinessErrorCode errorCode, String message, HttpServletRequest request) {
        String detail = (message == null || message.isBlank()) ? errorCode.getHttpStatus().getReasonPhrase() : message;
        HttpStatusCode status = HttpStatusCode.valueOf(errorCode.getHttpStatus().value());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(errorCode.getHttpStatus().getReasonPhrase());
        if (request != null && request.getRequestURI() != null && !request.getRequestURI().isBlank()) {
            problemDetail.setInstance(URI.create(request.getRequestURI()));
        }
        problemDetail.setProperty("code", errorCode.getCode());
        problemDetail.setProperty("message", detail);
        problemDetail.setProperty("traceId", TraceIdExtractor.currentTraceId());
        problemDetail.setProperty("requestId", TraceIdExtractor.currentRequestId());
        problemDetail.setProperty("service", SERVICE_NAME);

        // Optional fields from MDC if they exist
        String operation = MDC.get("operation");
        if (operation != null) {
            problemDetail.setProperty("operation", operation);
        }

        String retryable = MDC.get("retryable");
        if (retryable != null) {
            problemDetail.setProperty("retryable", Boolean.valueOf(retryable));
        }

        String downstreamService = MDC.get("downstreamService");
        if (downstreamService != null) {
            problemDetail.setProperty("downstreamService", downstreamService);
        }

        return problemDetail;
    }
}
