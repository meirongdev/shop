package dev.meirong.shop.gateway.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CapturingFilterChain implements FilterChain {

    private HttpServletRequest request;
    private HttpServletResponse response;
    private boolean invoked;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        this.request = (HttpServletRequest) request;
        this.response = (HttpServletResponse) response;
        this.invoked = true;
    }

    public HttpServletRequest request() {
        return request;
    }

    public HttpServletResponse response() {
        return response;
    }

    public boolean invoked() {
        return invoked;
    }
}
