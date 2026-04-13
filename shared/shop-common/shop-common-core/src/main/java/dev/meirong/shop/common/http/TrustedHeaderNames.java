package dev.meirong.shop.common.http;

public final class TrustedHeaderNames {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String TRACE_ID = "X-Trace-Id";
    public static final String BUYER_ID = "X-Buyer-Id";
    public static final String SELLER_ID = "X-Seller-Id";
    public static final String USERNAME = "X-Username";
    public static final String PORTAL = "X-Portal";
    public static final String ROLES = "X-Roles";
    public static final String ORDER_ID = "X-Order-Id";

    private TrustedHeaderNames() {
    }
}
