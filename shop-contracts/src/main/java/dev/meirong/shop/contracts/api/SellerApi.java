package dev.meirong.shop.contracts.api;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class SellerApi {

    public static final String BASE_PATH = "/seller/v1";
    public static final String DASHBOARD = BASE_PATH + "/dashboard/get";
    public static final String PRODUCT_CREATE = BASE_PATH + "/product/create";
    public static final String PRODUCT_UPDATE = BASE_PATH + "/product/update";
    public static final String PROMOTION_CREATE = BASE_PATH + "/promotion/create";

    // New e-commerce paths
    public static final String ORDER_LIST = BASE_PATH + "/order/list";
    public static final String ORDER_GET = BASE_PATH + "/order/get";
    public static final String ORDER_SHIP = BASE_PATH + "/order/ship";
    public static final String ORDER_DELIVER = BASE_PATH + "/order/deliver";
    public static final String ORDER_CANCEL = BASE_PATH + "/order/cancel";
    public static final String WALLET_GET = BASE_PATH + "/wallet/get";
    public static final String WALLET_WITHDRAW = BASE_PATH + "/wallet/withdraw";
    public static final String PROFILE_GET = BASE_PATH + "/profile/get";
    public static final String PROFILE_UPDATE = BASE_PATH + "/profile/update";
    public static final String COUPON_CREATE = BASE_PATH + "/coupon/create";
    public static final String COUPON_LIST = BASE_PATH + "/coupon/list";
    public static final String SHOP_GET = BASE_PATH + "/shop/get";
    public static final String SHOP_UPDATE = BASE_PATH + "/shop/update";

    private SellerApi() {
    }

    public record SellerContextRequest(@NotBlank String sellerId) {
    }

    public record DashboardResponse(long productCount,
                                    long activePromotionCount,
                                    List<MarketplaceApi.ProductResponse> products,
                                    List<PromotionApi.OfferResponse> promotions) {
    }
}
