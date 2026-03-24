package dev.meirong.shop.gateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class TraceCorrelationResponseFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "X-Request-Id";
    private static final String TRACE_ID = "X-Trace-Id";

    private final Tracer tracer;

    public TraceCorrelationResponseFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = currentTraceId();
        chain.doFilter(request, response);
        if (traceId.isBlank()) {
            traceId = currentTraceId();
        }
        setIfPresent(response, REQUEST_ID, request.getHeader(REQUEST_ID));
        setIfPresent(response, TRACE_ID, traceId);
    }

    private String currentTraceId() {
        Span span = tracer.currentSpan();
        return span == null ? "" : span.context().traceId();
    }

    private static void setIfPresent(HttpServletResponse response, String headerName, String value) {
        if (value != null && !value.isBlank()) {
            response.setHeader(headerName, value);
        }
    }
}
