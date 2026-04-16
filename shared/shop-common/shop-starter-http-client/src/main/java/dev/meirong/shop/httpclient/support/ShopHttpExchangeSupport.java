package dev.meirong.shop.httpclient.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.httpclient.error.SharedDownstreamErrorHandler;
import dev.meirong.shop.httpclient.interceptor.TracingHeaderInterceptor;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Factory for creating {@code @HttpExchange} client proxies with shared infrastructure:
 * <ul>
 *   <li>Tracing propagation via {@link TracingHeaderInterceptor}</li>
 *   <li>Observation integration via auto-configured {@code RestClient.Builder}</li>
 *   <li>Unified error handling via {@link SharedDownstreamErrorHandler}</li>
 * </ul>
 *
 * <p>This class should be created by {@code ShopHttpClientAutoConfiguration} and
 * injected into BFF configuration classes. Each client proxy is bound to a single
 * downstream service (specified by {@code baseUrl}).
 *
 * <p><b>Important:</b> The {@code RestClient.Builder} passed to this class MUST be
 * the auto-configured one from Spring Boot, not a manually constructed instance.
 * Only the auto-configured builder includes {@code ObservationRestClientCustomizer},
 * which attaches Micrometer Tracing interceptors automatically.
 *
 * @see <a href="https://github.com/spring-projects/spring-boot/issues/42502">spring-boot#42502: RestClient trace propagation</a>
 * @see <a href="https://docs.spring.io/spring-boot/reference/actuator/tracing.html">Spring Boot Tracing Docs</a>
 */
public class ShopHttpExchangeSupport {

    private final RestClient.Builder restClientBuilder;
    private final SharedDownstreamErrorHandler errorHandler;
    private final TracingHeaderInterceptor tracingInterceptor;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public ShopHttpExchangeSupport(RestClient.Builder restClientBuilder,
                                   ObjectMapper objectMapper,
                                   TracingHeaderInterceptor tracingInterceptor,
                                   @Nullable Duration connectTimeout,
                                   @Nullable Duration readTimeout) {
        this.restClientBuilder = restClientBuilder;
        this.errorHandler = new SharedDownstreamErrorHandler(objectMapper);
        this.tracingInterceptor = tracingInterceptor;
        this.connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(2);
        this.readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(5);
    }

    /**
     * Creates an {@code @HttpExchange} client proxy for the given interface.
     *
     * @param baseUrl    the base URL of the downstream service (e.g. "http://order-service:8080")
     * @param clientClass the {@code @HttpExchange} interface to proxy
     * @param <T>         the client type
     * @return a proxy that implements the interface, with tracing and error handling applied
     */
    public <T> T createClient(String baseUrl, Class<T> clientClass) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        RestClient restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .requestInterceptor(tracingInterceptor)
                .defaultStatusHandler(HttpStatusCode::isError, errorHandler::handleError)
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(clientClass);
    }
}
