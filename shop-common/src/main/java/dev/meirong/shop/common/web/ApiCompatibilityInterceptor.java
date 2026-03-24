package dev.meirong.shop.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class ApiCompatibilityInterceptor implements HandlerInterceptor {

    private static final Pattern VERSIONED_PATH = Pattern.compile("/v(\\d+)(?=/|$)");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        resolveApiVersion(request.getRequestURI())
                .ifPresent(version -> response.setHeader(CompatibilityHeaderNames.API_VERSION, version));

        if (handler instanceof HandlerMethod handlerMethod) {
            ApiDeprecation deprecation = resolveDeprecation(handlerMethod);
            if (deprecation != null) {
                response.setHeader(CompatibilityHeaderNames.DEPRECATION, "true");
                response.setHeader(CompatibilityHeaderNames.DEPRECATED_SINCE, deprecation.since());
                if (StringUtils.hasText(deprecation.sunsetAt())) {
                    response.setHeader(CompatibilityHeaderNames.SUNSET, formatSunset(deprecation.sunsetAt()));
                }
                if (StringUtils.hasText(deprecation.replacement())) {
                    response.setHeader(CompatibilityHeaderNames.REPLACEMENT, deprecation.replacement());
                }
            }
        }
        return true;
    }

    static Optional<String> resolveApiVersion(String requestPath) {
        if (!StringUtils.hasText(requestPath)) {
            return Optional.empty();
        }
        Matcher matcher = VERSIONED_PATH.matcher(requestPath);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    @Nullable
    private ApiDeprecation resolveDeprecation(HandlerMethod handlerMethod) {
        ApiDeprecation onMethod = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ApiDeprecation.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ApiDeprecation.class);
    }

    private String formatSunset(String rawValue) {
        return parseAsInstant(rawValue)
                .or(() -> parseAsDate(rawValue))
                .map(dateTime -> DateTimeFormatter.RFC_1123_DATE_TIME.format(dateTime))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid sunsetAt value: " + rawValue + ". Use ISO-8601 instant or date"));
    }

    private Optional<java.time.OffsetDateTime> parseAsInstant(String rawValue) {
        try {
            return Optional.of(Instant.parse(rawValue).atOffset(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private Optional<java.time.OffsetDateTime> parseAsDate(String rawValue) {
        try {
            return Optional.of(LocalDate.parse(rawValue).atStartOfDay().atOffset(ZoneOffset.UTC));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }
}
