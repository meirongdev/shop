package dev.meirong.shop.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceCorrelationResponseFilterTest {

    @Test
    void echoesRequestIdEvenWhenTracerIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/buyer/orders");
        request.addHeader("X-Request-Id", "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TraceCorrelationResponseFilter(null).doFilter(request, response, (wrappedRequest, wrappedResponse) -> {
        });

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-123");
        assertThat(response.getHeader("X-Trace-Id")).isNull();
    }
}
