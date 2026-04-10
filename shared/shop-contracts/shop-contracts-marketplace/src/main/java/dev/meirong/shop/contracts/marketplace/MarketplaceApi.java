package dev.meirong.shop.contracts.marketplace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MarketplaceApi {

    public static final String BASE_PATH = "/marketplace/v1";
    public static final String LIST = BASE_PATH + "/product/list";
    public static final String CREATE = BASE_PATH + "/product/create";
    public static final String UPDATE = BASE_PATH + "/product/update";
    public static final String GET = BASE_PATH + "/product/get";
    public static final String SEARCH = BASE_PATH + "/product/search";
    public static final String CATEGORY_LIST = BASE_PATH + "/category/list";
    public static final String INVENTORY_DEDUCT = BASE_PATH + "/product/inventory/deduct";
    public static final String INVENTORY_RESTORE = BASE_PATH + "/product/inventory/restore";

    // Review endpoints
    public static final String REVIEW_CREATE = BASE_PATH + "/reviews";
    public static final String REVIEW_LIST = BASE_PATH + "/products/{productId}/reviews";

    // Variant endpoints
    public static final String VARIANT_LIST = BASE_PATH + "/products/{productId}/variants";
    public static final String VARIANT_CREATE = BASE_PATH + "/products/{productId}/variants";

    private MarketplaceApi() {
    }

    public record ListProductsRequest(boolean publishedOnly) {
    }

    public record UpsertProductRequest(String productId,
                                       @NotBlank String sellerId,
                                       @NotBlank String sku,
                                       @NotBlank String name,
                                       @NotBlank String description,
                                       @NotNull BigDecimal price,
                                       @NotNull Integer inventory,
                                       boolean published) {
    }

    public record ProductResponse(UUID id,
                                  String sellerId,
                                  String sku,
                                  String name,
                                  String description,
                                  BigDecimal price,
                                  Integer inventory,
                                  boolean published,
                                  String categoryId,
                                  String categoryName,
                                  String imageUrl,
                                  String status,
                                  int reviewCount,
                                  BigDecimal avgRating) {
    }

    public record ProductsView(List<ProductResponse> products) {
    }

    public record GetProductRequest(@NotBlank String productId) {
    }

    public record SearchProductsRequest(String query, String categoryId, int page, int size) {
    }

    public record ProductsPageView(List<ProductResponse> products, long total, int page, int size) {
    }

    public record CategoryResponse(UUID id, String name, String description) {
    }

    public record DeductInventoryRequest(@NotBlank String productId, @NotNull Integer quantity) {
    }

    public record RestoreInventoryRequest(@NotBlank String productId, @NotNull Integer quantity) {
    }

    // --- Review records ---

    public record CreateReviewRequest(
        @NotBlank String productId,
        String orderId,
        @NotNull Integer rating,
        String content,
        List<String> images
    ) {}

    public record ReviewResponse(
        String id,
        String productId,
        String buyerId,
        String orderId,
        int rating,
        String content,
        List<String> images,
        String status,
        Instant createdAt
    ) {}

    public record ReviewsPageResponse(
        List<ReviewResponse> reviews,
        long total,
        int page,
        int size,
        BigDecimal avgRating,
        long reviewCount
    ) {}

    // --- Variant records ---

    public record CreateVariantRequest(
        @NotBlank String variantName,
        @NotBlank String attributes,
        BigDecimal priceAdjust,
        @NotNull Integer inventory,
        String skuSuffix
    ) {}

    public record VariantResponse(
        String id,
        String productId,
        String variantName,
        String attributes,
        BigDecimal priceAdjust,
        int inventory,
        String skuSuffix,
        int displayOrder
    ) {}
}
