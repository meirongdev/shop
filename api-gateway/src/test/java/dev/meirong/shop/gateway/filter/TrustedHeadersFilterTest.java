package dev.meirong.shop.gateway.filter;

import dev.meirong.shop.gateway.config.GatewayProperties;
import dev.meirong.shop.gateway.support.CapturingFilterChain;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedHeadersFilterTest {

    private final GatewayProperties properties = new GatewayProperties(
            "change-this-to-a-32-byte-demo-secret",
            "internal-token",
            new GatewayProperties.RateLimit(100, 20),
            null);

    private final TrustedHeadersFilter filter = new TrustedHeadersFilter(properties);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void injectsTrustedHeadersAndStripsClientSpoofedValues() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buyer/orders");
        request.addHeader("X-Request-Id", "req-123");
        request.addHeader("X-Player-Id", "spoofed-player");
        request.addHeader("X-Roles", "ROLE_ADMIN");
        request.addHeader("X-Internal-Token", "spoofed-token");
        request.addHeader("X-Custom", "custom-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked()).isTrue();
        HttpHeaderAssertions.assertTrustedHeaders(chain.request());
        assertThat(chain.request().getHeader("X-Request-Id")).isEqualTo("req-123");
        assertThat(chain.request().getHeader("X-Custom")).isEqualTo("custom-value");
        assertThat(Collections.list(chain.request().getHeaders("X-Internal-Token"))).containsExactly("internal-token");
        assertThat(Collections.list(chain.request().getHeaderNames()))
                .contains("X-Request-Id", "X-Player-Id", "X-User-Id", "X-Username", "X-Roles", "X-Portal", "X-Internal-Token", "X-Custom")
                .doesNotContain("x-player-id");
    }

    @Test
    void exposesRequestIdInMdcOnlyForTheDurationOfTheRequest() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buyer/orders");
        request.addHeader("X-Request-Id", "req-456");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInMdc = new AtomicReference<>();

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> requestIdInMdc.set(MDC.get("requestId")));

        assertThat(requestIdInMdc.get()).isEqualTo("req-456");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void skipsNonApiRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/buyer/home");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked()).isTrue();
        assertThat(chain.request()).isSameAs(request);
    }

    @Test
    void keepsApiRequestsUntouchedWhenNoJwtAuthenticationExists() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/buyer/orders");
        request.addHeader("X-Player-Id", "client-player");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingFilterChain chain = new CapturingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked()).isTrue();
        assertThat(chain.request()).isSameAs(request);
        assertThat(chain.request().getHeader("X-Player-Id")).isEqualTo("client-player");
    }

    private Jwt jwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .claim("principalId", "buyer-100")
                .claim("username", "alice")
                .claim("roles", List.of("ROLE_BUYER"))
                .claim("portal", "buyer")
                .build();
    }

    private static final class HttpHeaderAssertions {

        private static void assertTrustedHeaders(jakarta.servlet.http.HttpServletRequest request) {
            assertThat(request.getHeader("X-Player-Id")).isEqualTo("buyer-100");
            assertThat(request.getHeader("X-User-Id")).isEqualTo("buyer-100");
            assertThat(request.getHeader("X-Username")).isEqualTo("alice");
            assertThat(request.getHeader("X-Roles")).isEqualTo("ROLE_BUYER");
            assertThat(request.getHeader("X-Portal")).isEqualTo("buyer");
            assertThat(request.getHeader("X-Internal-Token")).isEqualTo("internal-token");
        }
    }
}
