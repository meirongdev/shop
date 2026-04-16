package dev.meirong.shop.httpclient.interceptor;

import dev.meirong.shop.common.http.TrustedHeaderNames;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.Tracer;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * {@link ClientHttpRequestInterceptor} that propagates business-context baggage
 * values as HTTP headers on outgoing requests.
 *
 * <p>This interceptor reads values from Micrometer baggage and writes them to the request
 * headers. This ensures that contextual information like buyer ID, portal, and username
 * travels with every downstream HTTP call made through {@link org.springframework.web.client.RestClient}
 * or {@link org.springframework.web.service.annotation.HttpExchange} proxies.
 *
 * <p>Usage: register this interceptor on the {@code RestClient.Builder} before
 * creating {@code @HttpExchange} clients. The fields to propagate are configured
 * via {@code management.tracing.baggage.remote-fields} in {@code application.yml}.
 *
 * @see Tracer
 * @see <a href="https://docs.spring.io/spring-boot/reference/actuator/tracing.html">Spring Boot Tracing Docs</a>
 */
public class TracingHeaderInterceptor implements ClientHttpRequestInterceptor {

    private final @Nullable Tracer tracer;
    private final List<BaggageMapping> baggageMappings;

    public TracingHeaderInterceptor(@Nullable Tracer tracer, List<BaggageMapping> baggageMappings) {
        this.tracer = tracer;
        this.baggageMappings = baggageMappings;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (tracer == null) {
            return execution.execute(request, body);
        }
        for (BaggageMapping mapping : baggageMappings) {
            Baggage baggage = tracer.getBaggage(mapping.baggageName());
            String value = baggage != null ? baggage.get() : null;
            if (value != null && !value.isBlank()) {
                request.getHeaders().set(mapping.headerName(), value);
            }
        }
        return execution.execute(request, body);
    }

    /**
     * A mapping between a Micrometer baggage entry and the HTTP header name
     * that should be set on outgoing requests.
     */
    public record BaggageMapping(String baggageName, String headerName) {

        public static BaggageMapping of(String headerName) {
            return new BaggageMapping(headerName, headerName);
        }

        public static List<BaggageMapping> defaults() {
            return List.of(
                    of(TrustedHeaderNames.BUYER_ID),
                    of(TrustedHeaderNames.SELLER_ID),
                    of(TrustedHeaderNames.USERNAME),
                    of(TrustedHeaderNames.PORTAL),
                    of(TrustedHeaderNames.ROLES),
                    of(TrustedHeaderNames.ORDER_ID));
        }

    }
}
