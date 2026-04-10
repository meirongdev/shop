package dev.meirong.shop.search.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.marketplace.MarketplaceInternalApi;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface MarketplaceInternalExchange {

    @GetExchange(MarketplaceInternalApi.LIST_ALL_PRODUCTS)
    ApiResponse<MarketplaceInternalApi.PagedProductsResponse> fetchProducts(
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}
