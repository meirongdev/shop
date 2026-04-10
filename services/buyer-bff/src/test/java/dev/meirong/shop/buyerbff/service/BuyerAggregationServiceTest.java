package dev.meirong.shop.buyerbff.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.buyerbff.client.SearchServiceClient;
import dev.meirong.shop.buyerbff.config.BuyerClientProperties;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.resilience.ResilienceHelper;
import dev.meirong.shop.contracts.marketplace.MarketplaceApi;
import java.net.http.HttpClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class BuyerAggregationServiceTest {

    private BuyerAggregationService service;
    private SearchServiceClient searchServiceClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.build()).thenReturn(mock(RestClient.class));
        searchServiceClient = mock(SearchServiceClient.class);
        ResilienceHelper resilienceHelper = mock(ResilienceHelper.class);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(resilienceHelper).read(anyString(), org.mockito.ArgumentMatchers.<Supplier<Object>>any(),
                        org.mockito.ArgumentMatchers.<Function<Throwable, Object>>any());
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(resilienceHelper).write(anyString(), org.mockito.ArgumentMatchers.<Supplier<Object>>any(),
                        org.mockito.ArgumentMatchers.<Function<Throwable, Object>>any());
        service = new BuyerAggregationService(
                builder,
                searchServiceClient,
                new BuyerClientProperties(
                        "http://profile",
                        "http://promotion",
                        "http://wallet",
                        "http://marketplace",
                        "http://order",
                        "http://search",
                        "http://loyalty",
                        Duration.ofHours(48),
                        HttpClient.Version.HTTP_1_1,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5)),
                resilienceHelper,
                mock(GuestCartStore.class),
                new ObjectMapper());
    }

    @Test
    void searchProducts_rejectsEmptyHttpExchangePayload() {
        when(searchServiceClient.searchProducts("serum", "cat-1", 1, 20))
                .thenReturn(new ApiResponse<>(null, "SC_OK", "Success", null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.searchProducts(new MarketplaceApi.SearchProductsRequest("serum", "cat-1", 1, 20)));

        assertEquals(CommonErrorCode.DOWNSTREAM_ERROR, exception.getErrorCode());
    }

    @Test
    void validateCouponFallback_rethrowsDomainCouponErrors() {
        BusinessException exception = new BusinessException(CommonErrorCode.COUPON_INVALID, "Coupon invalid");

        assertThrows(BusinessException.class, () ->
                service.validateCouponForCheckoutFallback("SAVE10", BigDecimal.TEN, exception));
    }

    @Test
    void validateCouponFallback_skipsOnSystemFailures() {
        BusinessException exception = new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "promotion unavailable");

        assertNull(service.validateCouponForCheckoutFallback("SAVE10", BigDecimal.TEN, exception));
    }

    @Test
    void deductLoyaltyFallback_rethrowsInsufficientBalance() {
        BusinessException exception = new BusinessException(CommonErrorCode.INSUFFICIENT_BALANCE, "Insufficient balance");

        assertThrows(BusinessException.class, () ->
                service.deductLoyaltyPointsForCheckoutFallback("buyer-1", 500, "ref-1", "remark", exception));
    }

    @Test
    void deductLoyaltyFallback_skipsOnSystemFailures() {
        BusinessException exception = new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "loyalty unavailable");

        assertFalse(service.deductLoyaltyPointsForCheckoutFallback("buyer-1", 500, "ref-1", "remark", exception));
    }

    @Test
    void deductInventoryFallback_keepsMarketplaceAsFailFastCoreDependency() {
        BusinessException exception = new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "marketplace unavailable");

        assertThrows(BusinessException.class, () ->
                service.deductInventoryForCheckoutFallback("product-1", 1, exception));
    }
}
