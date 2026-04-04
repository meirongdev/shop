package dev.meirong.shop.buyerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.api.SearchApi;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface SearchServiceClient {

    @GetExchange(SearchApi.SEARCH_PRODUCTS)
    ApiResponse<SearchApi.SearchProductsResponse> searchProducts(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "categoryId", required = false) String categoryId,
            @RequestParam("page") int page,
            @RequestParam("hitsPerPage") int hitsPerPage);
}
