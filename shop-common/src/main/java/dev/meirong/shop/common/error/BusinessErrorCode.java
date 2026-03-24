package dev.meirong.shop.common.error;

import org.springframework.http.HttpStatus;

public interface BusinessErrorCode {

    String getCode();

    HttpStatus getHttpStatus();
}
