package dev.meirong.shop.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TrustedHeadersRequestWrapper extends HttpServletRequestWrapper {

    private static final String REQUEST_ID = "X-Request-Id";
    private static final String BUYER_ID = "X-Buyer-Id";
    private static final String USERNAME = "X-Username";
    private static final String ROLES = "X-Roles";
    private static final String PORTAL = "X-Portal";
    private static final String INTERNAL_TOKEN = "X-Internal-Token";

    private static final Set<String> STRIPPED_HEADERS = Set.of(
            BUYER_ID.toLowerCase(Locale.ROOT),
            "x-player-id",
            "x-user-id",
            USERNAME.toLowerCase(Locale.ROOT),
            ROLES.toLowerCase(Locale.ROOT),
            PORTAL.toLowerCase(Locale.ROOT),
            INTERNAL_TOKEN.toLowerCase(Locale.ROOT));

    private final Map<String, String> injectedHeadersByLowercase;
    private final Map<String, String> canonicalHeaderNames;

    public TrustedHeadersRequestWrapper(HttpServletRequest request,
                                        String requestId,
                                        String buyerId,
                                        String username,
                                        String roles,
                                        String portal,
                                        String internalToken) {
        super(request);
        Map<String, String> headers = new LinkedHashMap<>();
        putIfPresent(headers, REQUEST_ID, requestId);
        putIfPresent(headers, BUYER_ID, buyerId);
        putIfPresent(headers, USERNAME, username);
        putIfPresent(headers, ROLES, roles);
        putIfPresent(headers, PORTAL, portal);
        putIfPresent(headers, INTERNAL_TOKEN, internalToken);

        this.injectedHeadersByLowercase = new LinkedHashMap<>();
        this.canonicalHeaderNames = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            String key = normalize(name);
            injectedHeadersByLowercase.put(key, value);
            canonicalHeaderNames.put(key, name);
        });
    }

    @Override
    public String getHeader(String name) {
        String key = normalize(name);
        if (injectedHeadersByLowercase.containsKey(key) || STRIPPED_HEADERS.contains(key)) {
            return injectedHeadersByLowercase.get(key);
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String value = getHeader(name);
        return value != null
                ? Collections.enumeration(List.of(value))
                : Collections.emptyEnumeration();
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> headerNames = new LinkedHashSet<>();
        Collections.list(super.getHeaderNames()).stream()
                .filter(name -> !STRIPPED_HEADERS.contains(normalize(name)))
                .filter(name -> !injectedHeadersByLowercase.containsKey(normalize(name)))
                .forEach(headerNames::add);
        headerNames.addAll(canonicalHeaderNames.values());
        return Collections.enumeration(headerNames);
    }

    private static void putIfPresent(Map<String, String> target, String name, String value) {
        if (value != null && !value.isBlank()) {
            target.put(name, value);
        }
    }

    private static String normalize(String headerName) {
        return headerName == null ? "" : headerName.toLowerCase(Locale.ROOT);
    }
}
