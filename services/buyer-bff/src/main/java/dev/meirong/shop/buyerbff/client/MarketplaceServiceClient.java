package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.marketplace.MarketplaceApi;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface MarketplaceServiceClient {

    @PostExchange(MarketplaceApi.LIST)
    ApiResponse<MarketplaceApi.ProductsView> listProducts(
            @RequestBody MarketplaceApi.ListProductsRequest request);

    @PostExchange(MarketplaceApi.GET)
    ApiResponse<MarketplaceApi.ProductResponse> getProduct(
            @RequestBody MarketplaceApi.GetProductRequest request);

    @PostExchange(MarketplaceApi.SEARCH)
    ApiResponse<MarketplaceApi.ProductsPageView> searchProducts(
            @RequestBody MarketplaceApi.SearchProductsRequest request);

    @PostExchange(MarketplaceApi.CATEGORY_LIST)
    ApiResponse<List<MarketplaceApi.CategoryResponse>> listCategories();

    @PostExchange(MarketplaceApi.INVENTORY_DEDUCT)
    ApiResponse<Void> deductInventory(
            @RequestBody MarketplaceApi.DeductInventoryRequest request);

    @PostExchange(MarketplaceApi.INVENTORY_RESTORE)
    ApiResponse<Void> restoreInventory(
            @RequestBody MarketplaceApi.RestoreInventoryRequest request);
}
