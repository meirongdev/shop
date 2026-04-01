package dev.meirong.shop.common.error;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements BusinessErrorCode {
    VALIDATION_ERROR("SC_VALIDATION_ERROR", HttpStatus.BAD_REQUEST),
    TOO_MANY_REQUESTS("SC_TOO_MANY_REQUESTS", HttpStatus.TOO_MANY_REQUESTS),
    UNAUTHORIZED("SC_UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("SC_FORBIDDEN", HttpStatus.FORBIDDEN),
    NOT_FOUND("SC_NOT_FOUND", HttpStatus.NOT_FOUND),
    DOWNSTREAM_ERROR("SC_DOWNSTREAM_ERROR", HttpStatus.BAD_GATEWAY),
    INTERNAL_ERROR("SC_INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR),
    FEATURE_DISABLED("SC_FEATURE_DISABLED", HttpStatus.SERVICE_UNAVAILABLE),
    PAYMENT_PROVIDER_DISABLED("SC_PAYMENT_PROVIDER_DISABLED", HttpStatus.SERVICE_UNAVAILABLE),
    INSUFFICIENT_BALANCE("SC_INSUFFICIENT_BALANCE", HttpStatus.BAD_REQUEST),
    INVENTORY_INSUFFICIENT("SC_INVENTORY_INSUFFICIENT", HttpStatus.BAD_REQUEST),
    COUPON_INVALID("SC_COUPON_INVALID", HttpStatus.BAD_REQUEST),
    COUPON_EXPIRED("SC_COUPON_EXPIRED", HttpStatus.BAD_REQUEST),
    CART_EMPTY("SC_CART_EMPTY", HttpStatus.BAD_REQUEST);

    private final String code;
    private final HttpStatus httpStatus;

    CommonErrorCode(String code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
