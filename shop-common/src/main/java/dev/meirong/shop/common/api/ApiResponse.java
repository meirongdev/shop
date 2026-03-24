package dev.meirong.shop.common.api;

import dev.meirong.shop.common.error.BusinessErrorCode;
import dev.meirong.shop.common.trace.TraceIdExtractor;

public record ApiResponse<T>(String traceId, String status, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(TraceIdExtractor.currentTraceId(), "SC_OK", "Success", data);
    }

    public static ApiResponse<Void> failure(BusinessErrorCode errorCode, String message) {
        return new ApiResponse<>(TraceIdExtractor.currentTraceId(), errorCode.getCode(), message, null);
    }
}
