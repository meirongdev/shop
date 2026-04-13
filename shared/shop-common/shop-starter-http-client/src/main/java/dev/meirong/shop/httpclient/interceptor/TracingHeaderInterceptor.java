package dev.meirong.shop.httpclient.interceptor;

import dev.meirong.shop.common.http.TrustedHeaderNames;
import io.micrometer.tracing.BaggageField;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * {@link ClientHttpRequestInterceptor} that propagates business-context baggage
 * values as HTTP headers on outgoing requests.
 *
 * <p>This interceptor reads values from {@link BaggageField} (which are populated
 * by Micrometer Tracing when baggage is set in scope) and writes them to the
 * request headers. This ensures that contextual information like buyer ID,
 * portal, and username travels with every downstream HTTP call made through
 * {@link org.springframework.web.client.RestClient} or
 * {@link org.springframework.web.service.annotation.HttpExchange} proxies.
 *
 * <p>Usage: register this interceptor on the {@code RestClient.Builder} before
 * creating {@code @HttpExchange} clients. The fields to propagate are configured
 * via {@code management.tracing.baggage.remote-fields} in {@code application.yml}.
 *
 * @see BaggageField
 * @see <a href="https://docs.spring.io/spring-boot/reference/actuator/tracing.html">Spring Boot Tracing Docs</a>
 */
public class TracingHeaderInterceptor implements ClientHttpRequestInterceptor {

    private final List<BaggageMapping> baggageMappings;

    public TracingHeaderInterceptor(List<BaggageMapping> baggageMappings) {
        this.baggageMappings = baggageMappings;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        for (BaggageMapping mapping : baggageMappings) {
            String value = mapping.field().getValue();
            if (value != null && !value.isBlank()) {
                request.getHeaders().set(mapping.headerName(), value);
            }
        }
        return execution.execute(request, body);
    }

    /**
     * A mapping between a {@link BaggageField} and the HTTP header name
     * that should be set on outgoing requests.
     */
    public record BaggageMapping(BaggageField field, String headerName) {

        public static BaggageMapping of(String headerName) {
            return new BaggageMapping(BaggageField.create(headerName), headerName);
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

        // Convenience accessors for common fields (used in tests and config)
        public static BaggageField buyerIdField() {
            return BaggageField.create(TrustedHeaderNames.BUYER_ID);
        }

        public static BaggageField sellerIdField() {
            return BaggageField.create(TrustedHeaderNames.SELLER_ID);
        }

        public static BaggageField usernameField() {
            return BaggageField.create(TrustedHeaderNames.USERNAME);
        }

        public static BaggageField portalField() {
            return BaggageField.create(TrustedHeaderNames.PORTAL);
        }

        public static BaggageField rolesField() {
            return BaggageField.create(TrustedHeaderNames.ROLES);
        }

        public static BaggageField orderIdField() {
            return BaggageField.create(TrustedHeaderNames.ORDER_ID);
        }
    }
}
