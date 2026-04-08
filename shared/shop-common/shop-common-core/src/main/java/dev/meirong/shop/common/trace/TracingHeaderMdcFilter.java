package dev.meirong.shop.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TracingHeaderMdcFilter extends OncePerRequestFilter {

    private static final String BUYER_ID_HEADER = "X-Buyer-Id";
    private static final String SELLER_ID_HEADER = "X-Seller-Id";
    private static final String USERNAME_HEADER = "X-Username";
    private static final String PORTAL_HEADER = "X-Portal";
    private static final String ORDER_ID_HEADER = "X-Order-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        putIfPresent(request, BUYER_ID_HEADER, "buyerId");
        putIfPresent(request, SELLER_ID_HEADER, "sellerId");
        putIfPresent(request, USERNAME_HEADER, "username");
        putIfPresent(request, PORTAL_HEADER, "portal");
        putIfPresent(request, ORDER_ID_HEADER, "orderId");

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("buyerId");
            MDC.remove("sellerId");
            MDC.remove("username");
            MDC.remove("portal");
            MDC.remove("orderId");
        }
    }

    private void putIfPresent(HttpServletRequest request, String headerName, String mdcKey) {
        String value = request.getHeader(headerName);
        if (value != null && !value.isBlank()) {
            MDC.put(mdcKey, value);
        }
    }
}
