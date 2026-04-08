package dev.meirong.shop.gateway.filter;

import dev.meirong.shop.gateway.config.GatewayProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 10)
public class TrustedHeadersFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "X-Request-Id";

    private final GatewayProperties properties;

    public TrustedHeadersFilter(GatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            chain.doFilter(request, response);
            return;
        }

        String requestId = MDC.get("requestId");
        if (requestId == null || requestId.isBlank()) {
            requestId = request.getHeader(REQUEST_ID);
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        List<String> roles = Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of());

        TrustedHeadersRequestWrapper wrapped = new TrustedHeadersRequestWrapper(
                request,
                requestId,
                jwt.getClaimAsString("principalId"),
                jwt.getClaimAsString("username"),
                String.join(",", roles),
                jwt.getClaimAsString("portal"));

        String principalId = jwt.getClaimAsString("principalId");
        String username = jwt.getClaimAsString("username");
        String portal = jwt.getClaimAsString("portal");

        if (principalId != null) {
            MDC.put("principalId", principalId);
            if ("BUYER".equalsIgnoreCase(portal)) {
                MDC.put("buyerId", principalId);
            } else if ("SELLER".equalsIgnoreCase(portal)) {
                MDC.put("sellerId", principalId);
            }
        }
        if (username != null) {
            MDC.put("username", username);
        }
        if (portal != null) {
            MDC.put("portal", portal);
        }

        try {
            chain.doFilter(wrapped, response);
        } finally {
            MDC.remove("principalId");
            MDC.remove("buyerId");
            MDC.remove("sellerId");
            MDC.remove("username");
            MDC.remove("portal");
        }
    }
}
