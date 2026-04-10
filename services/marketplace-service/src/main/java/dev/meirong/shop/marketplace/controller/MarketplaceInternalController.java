package dev.meirong.shop.marketplace.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.marketplace.MarketplaceInternalApi;
import dev.meirong.shop.marketplace.service.MarketplaceApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(MarketplaceInternalApi.BASE_PATH)
public class MarketplaceInternalController {

    private final MarketplaceApplicationService marketplaceService;

    public MarketplaceInternalController(MarketplaceApplicationService marketplaceService) {
        this.marketplaceService = marketplaceService;
    }

    @GetMapping("/products")
    public ApiResponse<MarketplaceInternalApi.PagedProductsResponse> listAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "500") int size) {
        return ApiResponse.success(marketplaceService.listAllProductsPaged(page, size));
    }
}
