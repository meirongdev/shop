package dev.meirong.shop.common.web;

import dev.meirong.shop.common.error.BusinessErrorCode;
import dev.meirong.shop.common.trace.TraceIdExtractor;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;

public final class ShopProblemDetails {

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
        return problemDetail;
    }
}
