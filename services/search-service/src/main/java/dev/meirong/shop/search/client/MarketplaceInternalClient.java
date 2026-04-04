package dev.meirong.shop.search.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.api.MarketplaceInternalApi;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MarketplaceInternalClient {

    private final MarketplaceInternalExchange marketplaceInternalExchange;

    public MarketplaceInternalClient(MarketplaceInternalExchange marketplaceInternalExchange) {
        this.marketplaceInternalExchange = marketplaceInternalExchange;
    }

    public MarketplaceInternalApi.PagedProductsResponse fetchProducts(int page, int size) {
        try {
            ApiResponse<MarketplaceInternalApi.PagedProductsResponse> response =
                    marketplaceInternalExchange.fetchProducts(page, size);
            if (response == null || response.data() == null) {
                throw new BusinessException(
                        CommonErrorCode.DOWNSTREAM_ERROR,
                        "Failed to fetch products from marketplace-service");
            }
            return response.data();
        } catch (RestClientResponseException exception) {
            throw new BusinessException(
                    CommonErrorCode.DOWNSTREAM_ERROR,
                    "Failed to fetch products from marketplace-service: " + exception.getMessage(),
                    exception);
        } catch (RestClientException exception) {
            throw new BusinessException(
                    CommonErrorCode.DOWNSTREAM_ERROR,
                    "Marketplace-service is unavailable",
                    exception);
        }
    }
}
