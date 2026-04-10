package dev.meirong.shop.contracts.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class ProfileApi {

    public static final String BASE_PATH = "/profile/v1";
    public static final String GET = BASE_PATH + "/profile/get";
    public static final String UPDATE = BASE_PATH + "/profile/update";
    public static final String SELLER_GET = BASE_PATH + "/seller/get";
    public static final String SELLER_UPDATE = BASE_PATH + "/seller/update";
    public static final String SELLER_STOREFRONT = BASE_PATH + "/seller/storefront";
    public static final String SELLER_SHOP_UPDATE = BASE_PATH + "/seller/shop/update";

    private ProfileApi() {
    }

    public record GetProfileRequest(
            @Schema(description = "买家 ID", example = "buyer-1001")
            @NotBlank String buyerId) {
    }

    public record UpdateProfileRequest(
            @Schema(description = "买家 ID", example = "buyer-1001")
            @NotBlank String buyerId,
            @Schema(description = "展示名", example = "Buyer Demo")
            @NotBlank String displayName,
            @Schema(description = "联系邮箱", example = "buyer.demo@example.com")
            @Email String email,
            @Schema(description = "会员等级", example = "SILVER")
            @NotBlank String tier) {
    }

    public record UpdateShopRequest(@NotBlank String sellerId,
                                    String shopName,
                                    String shopSlug,
                                    String shopDescription,
                                    String logoUrl,
                                    String bannerUrl) {
    }

    public record ProfileResponse(
            @Schema(description = "买家 ID", example = "buyer-1001")
            String buyerId,
            @Schema(description = "用户名", example = "buyer.demo")
            String username,
            @Schema(description = "展示名", example = "Buyer Demo")
            String displayName,
            @Schema(description = "邮箱", example = "buyer.demo@example.com")
            String email,
            @Schema(description = "等级", example = "SILVER")
            String tier,
            @Schema(description = "创建时间", example = "2026-03-23T12:00:00Z")
            Instant createdAt,
            @Schema(description = "更新时间", example = "2026-03-23T12:30:00Z")
            Instant updatedAt) {
    }

    public record SellerStorefrontResponse(String sellerId,
                                            String username,
                                            String displayName,
                                            String shopName,
                                            String shopSlug,
                                            String shopDescription,
                                            String logoUrl,
                                            String bannerUrl,
                                            double avgRating,
                                            int totalSales,
                                            String tier,
                                            Instant createdAt) {
    }
}
