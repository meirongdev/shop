package dev.meirong.shop.common.web;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Override Spring Boot's default ProblemDetail handler for validation errors.
     * Without this, Spring's built-in ProblemDetailsHandler takes priority and
     * returns its own format (missing traceId, requestId, code, service fields).
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        HttpServletRequest httpRequest = ((ServletWebRequest) request).getRequest();
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        ProblemDetail pd = ShopProblemDetails.from(CommonErrorCode.VALIDATION_ERROR, message, httpRequest);
        return new ResponseEntity<>(pd, HttpStatus.valueOf(status.value()));
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException exception, HttpServletRequest request) {
        return ShopProblemDetails.from(exception.getErrorCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        return ShopProblemDetails.from(CommonErrorCode.VALIDATION_ERROR, exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception exception, HttpServletRequest request) {
        return ShopProblemDetails.from(CommonErrorCode.INTERNAL_ERROR, exception.getMessage(), request);
    }
}
