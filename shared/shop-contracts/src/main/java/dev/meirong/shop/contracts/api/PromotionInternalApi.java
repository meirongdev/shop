package dev.meirong.shop.contracts.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class PromotionInternalApi {

    public static final String BASE_PATH = "/promotion/internal";
    public static final String BUYER_AVAILABLE_COUPONS = BASE_PATH + "/coupon/buyer";

    private PromotionInternalApi() {
    }

    public record BuyerCouponsRequest(@NotBlank String buyerId) {
    }

    public record BuyerCouponSummary(
            @Schema(description = "优惠券实例编码", example = "WELCOME-5OFF-I")
            String code,
            @Schema(description = "优惠券标题", example = "$5 Off Welcome")
            String title,
            @Schema(description = "到期时间", example = "2026-04-06T12:00:00Z")
            Instant expiresAt) {
    }

    public record BuyerCouponsResponse(List<BuyerCouponSummary> coupons) {
    }
}
