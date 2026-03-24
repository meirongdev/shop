package dev.meirong.shop.common.web;

import dev.meirong.shop.common.http.TrustedHeaderNames;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalAccessFilter extends OncePerRequestFilter {

    private final InternalSecurityProperties properties;

    public InternalAccessFilter(InternalSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.excludedPathPrefixes().stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incomingToken = request.getHeader(TrustedHeaderNames.INTERNAL_TOKEN);
        if (incomingToken == null || !incomingToken.equals(properties.token())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid internal token");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
