package dev.meirong.shop.search.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.contracts.api.MarketplaceInternalApi;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceInternalClientTest {

    @Mock
    private MarketplaceInternalExchange marketplaceInternalExchange;

    private MarketplaceInternalClient client;

    @BeforeEach
    void setUp() {
        client = new MarketplaceInternalClient(marketplaceInternalExchange);
    }

    @Test
    void fetchProducts_returnsPayloadData() {
        MarketplaceInternalApi.PagedProductsResponse response = new MarketplaceInternalApi.PagedProductsResponse(
                List.of(),
                0,
                1,
                0);
        when(marketplaceInternalExchange.fetchProducts(0, 100)).thenReturn(ApiResponse.success(response));

        assertThat(client.fetchProducts(0, 100)).isEqualTo(response);
    }

    @Test
    void fetchProducts_withEmptyPayloadThrowsBusinessException() {
        when(marketplaceInternalExchange.fetchProducts(0, 100))
                .thenReturn(new ApiResponse<>(null, "SC_OK", "Success", null));

        assertThatThrownBy(() -> client.fetchProducts(0, 100))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to fetch products from marketplace-service");
    }
}
