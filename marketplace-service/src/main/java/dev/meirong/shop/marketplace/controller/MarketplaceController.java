package dev.meirong.shop.marketplace.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.api.MarketplaceApi;
import dev.meirong.shop.marketplace.service.MarketplaceApplicationService;
import dev.meirong.shop.marketplace.service.ProductReviewService;
import dev.meirong.shop.marketplace.service.ProductVariantService;
import dev.meirong.shop.common.http.TrustedHeaderNames;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(MarketplaceApi.BASE_PATH)
public class MarketplaceController {

    private final MarketplaceApplicationService service;
    private final ProductReviewService reviewService;
    private final ProductVariantService variantService;

    public MarketplaceController(MarketplaceApplicationService service,
                                  ProductReviewService reviewService,
                                  ProductVariantService variantService) {
        this.service = service;
        this.reviewService = reviewService;
        this.variantService = variantService;
    }

    @PostMapping("/product/list")
    public ApiResponse<MarketplaceApi.ProductsView> listProducts(@Valid @RequestBody MarketplaceApi.ListProductsRequest request) {
        return ApiResponse.success(service.listProducts(request));
    }

    @PostMapping("/product/create")
    public ApiResponse<MarketplaceApi.ProductResponse> createProduct(@Valid @RequestBody MarketplaceApi.UpsertProductRequest request) {
        return ApiResponse.success(service.createProduct(request));
    }

    @PostMapping("/product/update")
    public ApiResponse<MarketplaceApi.ProductResponse> updateProduct(@Valid @RequestBody MarketplaceApi.UpsertProductRequest request) {
        return ApiResponse.success(service.updateProduct(request));
    }

    @PostMapping("/product/get")
    public ApiResponse<MarketplaceApi.ProductResponse> getProduct(@Valid @RequestBody MarketplaceApi.GetProductRequest request) {
        return ApiResponse.success(service.getProduct(request.productId()));
    }

    @PostMapping("/product/search")
    public ApiResponse<MarketplaceApi.ProductsPageView> searchProducts(@RequestBody MarketplaceApi.SearchProductsRequest request) {
        return ApiResponse.success(service.searchProducts(request));
    }

    @PostMapping("/category/list")
    public ApiResponse<List<MarketplaceApi.CategoryResponse>> listCategories() {
        return ApiResponse.success(service.listCategories());
    }

    @PostMapping("/product/inventory/deduct")
    public ApiResponse<Void> deductInventory(@Valid @RequestBody MarketplaceApi.DeductInventoryRequest request) {
        service.deductInventory(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/product/inventory/restore")
    public ApiResponse<Void> restoreInventory(@Valid @RequestBody MarketplaceApi.RestoreInventoryRequest request) {
        service.restoreInventory(request);
        return ApiResponse.success(null);
    }

    // --- Review endpoints ---

    @PostMapping("/reviews")
    public ApiResponse<MarketplaceApi.ReviewResponse> createReview(
            @Valid @RequestBody MarketplaceApi.CreateReviewRequest request,
            @RequestHeader(TrustedHeaderNames.PLAYER_ID) String buyerId) {
        return ApiResponse.success(reviewService.createReview(buyerId, request));
    }

    @GetMapping("/products/{productId}/reviews")
    public ApiResponse<MarketplaceApi.ReviewsPageResponse> getProductReviews(
            @PathVariable String productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(reviewService.getProductReviews(productId, page, size));
    }

    // --- Variant endpoints ---

    @GetMapping("/products/{productId}/variants")
    public ApiResponse<List<MarketplaceApi.VariantResponse>> getVariants(@PathVariable String productId) {
        return ApiResponse.success(variantService.getVariants(productId));
    }

    @PostMapping("/products/{productId}/variants")
    public ApiResponse<MarketplaceApi.VariantResponse> createVariant(
            @PathVariable String productId,
            @Valid @RequestBody MarketplaceApi.CreateVariantRequest request) {
        return ApiResponse.success(variantService.createVariant(productId, request));
    }
}
