package dev.meirong.shop.contracts.promotion;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PromotionApi {

    public static final String BASE_PATH = "/promotion/v1";
    public static final String LIST = BASE_PATH + "/offer/list";
    public static final String CREATE = BASE_PATH + "/offer/create";
    public static final String COUPON_CREATE = BASE_PATH + "/coupon/create";
    public static final String COUPON_LIST = BASE_PATH + "/coupon/list";
    public static final String COUPON_VALIDATE = BASE_PATH + "/coupon/validate";
    public static final String COUPON_APPLY = BASE_PATH + "/coupon/apply";
    public static final String CALCULATE = BASE_PATH + "/calculate";

    private PromotionApi() {
    }

    public record ListOffersRequest(String buyerId) {
    }

    public record CreateOfferRequest(@NotBlank String sellerId,
                                     @NotBlank String code,
                                     @NotBlank String title,
                                     @NotBlank String description,
                                     @NotNull BigDecimal rewardAmount) {
    }

    public record OfferResponse(UUID id,
                                String code,
                                String title,
                                String description,
                                BigDecimal rewardAmount,
                                boolean active,
                                String source,
                                Instant createdAt) {
    }

    public record OffersView(List<OfferResponse> offers) {
    }

    public record CreateCouponRequest(@NotBlank String sellerId,
                                      @NotBlank String code,
                                      @NotBlank String discountType,
                                      @NotNull BigDecimal discountValue,
                                      BigDecimal minOrderAmount,
                                      BigDecimal maxDiscount,
                                      Integer usageLimit,
                                      Instant expiresAt) {
    }

    public record CouponResponse(
            @Schema(description = "优惠券 ID", example = "00000000-0000-0000-0000-000000000001")
            UUID id,
            @Schema(description = "卖家 ID", example = "SYSTEM")
            String sellerId,
            @Schema(description = "优惠券编码", example = "WELCOME-5OFF-I")
            String code,
            @Schema(description = "优惠类型", example = "FIXED")
            String discountType,
            @Schema(description = "优惠面额", example = "5.00")
            BigDecimal discountValue,
            @Schema(description = "最低消费门槛", example = "20.00")
            BigDecimal minOrderAmount,
            @Schema(description = "最高优惠封顶", example = "10.00")
            BigDecimal maxDiscount,
            @Schema(description = "总可使用次数", example = "1")
            int usageLimit,
            @Schema(description = "已使用次数", example = "0")
            int usedCount,
            @Schema(description = "过期时间", example = "2026-04-06T12:00:00Z")
            Instant expiresAt,
            @Schema(description = "当前是否有效", example = "true")
            boolean active,
            @Schema(description = "创建时间", example = "2026-03-23T12:00:00Z")
            Instant createdAt) {
    }

    public record ValidateCouponRequest(@NotBlank String code,
                                        @NotNull BigDecimal orderAmount) {
    }

    public record CouponValidationResponse(boolean valid,
                                           String couponId,
                                           String code,
                                           String discountType,
                                           BigDecimal discountValue,
                                           BigDecimal discountAmount,
                                           String message) {
    }

    public record ApplyCouponRequest(@NotBlank String couponId,
                                     @NotBlank String buyerId,
                                     @NotBlank String orderId,
                                     @NotNull BigDecimal discountApplied) {
    }

    public record ListCouponsRequest(String sellerId) {
    }

    // ---- Promotion Engine ----

    public record CartItemDto(String productId, String categoryId, BigDecimal price, int quantity) {
    }

    public record CalculateRequest(@NotNull BigDecimal orderAmount,
                                   List<CartItemDto> items,
                                   String buyerId,
                                   String userTier,
                                   boolean isNewUser) {
    }

    public record DiscountLineResponse(String offerId, String offerCode, String offerTitle, BigDecimal discount) {
    }

    public record CalculateResponse(BigDecimal originalAmount,
                                    BigDecimal totalDiscount,
                                    BigDecimal finalAmount,
                                    List<DiscountLineResponse> appliedDiscounts) {
    }
}
