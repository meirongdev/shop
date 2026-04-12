package dev.meirong.shop.sellerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.marketplace.MarketplaceApi;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface MarketplaceServiceClient {

    @PostExchange(MarketplaceApi.LIST)
    ApiResponse<MarketplaceApi.ProductsView> listProducts(
            @RequestBody MarketplaceApi.ListProductsRequest request);

    @PostExchange(MarketplaceApi.CREATE)
    ApiResponse<MarketplaceApi.ProductResponse> createProduct(
            @RequestBody MarketplaceApi.UpsertProductRequest request);

    @PostExchange(MarketplaceApi.UPDATE)
    ApiResponse<MarketplaceApi.ProductResponse> updateProduct(
            @RequestBody MarketplaceApi.UpsertProductRequest request);

    @PostExchange(MarketplaceApi.SEARCH)
    ApiResponse<MarketplaceApi.ProductsPageView> searchProducts(
            @RequestBody MarketplaceApi.SearchProductsRequest request);
}
