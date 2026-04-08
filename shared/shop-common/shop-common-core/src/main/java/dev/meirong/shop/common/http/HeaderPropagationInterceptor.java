package dev.meirong.shop.common.http;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class HeaderPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest currentRequest = attributes.getRequest();
            propagateHeader(currentRequest, request, TrustedHeaderNames.REQUEST_ID);
            propagateHeader(currentRequest, request, TrustedHeaderNames.TRACE_ID);
            propagateHeader(currentRequest, request, TrustedHeaderNames.BUYER_ID);
            propagateHeader(currentRequest, request, TrustedHeaderNames.SELLER_ID);
            propagateHeader(currentRequest, request, TrustedHeaderNames.USERNAME);
            propagateHeader(currentRequest, request, TrustedHeaderNames.PORTAL);
            propagateHeader(currentRequest, request, TrustedHeaderNames.ROLES);
            propagateHeader(currentRequest, request, TrustedHeaderNames.ORDER_ID);
        }
        return execution.execute(request, body);
    }

    private void propagateHeader(HttpServletRequest source, HttpRequest target, String headerName) {
        String value = source.getHeader(headerName);
        if (value != null && !value.isBlank()) {
            target.getHeaders().set(headerName, value);
        }
    }
}
