package dev.meirong.shop.buyerbff.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import dev.meirong.shop.clients.loyalty.LoyaltyServiceClient;
import dev.meirong.shop.clients.marketplace.MarketplaceServiceClient;
import dev.meirong.shop.clients.order.OrderServiceClient;
import dev.meirong.shop.clients.profile.ProfileInternalServiceClient;
import dev.meirong.shop.clients.profile.ProfileServiceClient;
import dev.meirong.shop.clients.promotion.PromotionInternalServiceClient;
import dev.meirong.shop.clients.promotion.PromotionServiceClient;
import dev.meirong.shop.clients.search.SearchServiceClient;
import dev.meirong.shop.clients.wallet.WalletServiceClient;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.resilience.ResilienceHelper;
import dev.meirong.shop.contracts.marketplace.MarketplaceApi;
import java.math.BigDecimal;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class BuyerAggregationServiceTest {

    private BuyerAggregationService service;
    private SearchServiceClient searchServiceClient;

    @BeforeEach
    void setUp() {
        searchServiceClient = mock(SearchServiceClient.class);
        ResilienceHelper resilienceHelper = mock(ResilienceHelper.class);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(resilienceHelper).read(anyString(), ArgumentMatchers.<Supplier<Object>>any(),
                        ArgumentMatchers.<Function<Throwable, Object>>any());
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(resilienceHelper).write(anyString(), ArgumentMatchers.<Supplier<Object>>any(),
                        ArgumentMatchers.<Function<Throwable, Object>>any());
        service = new BuyerAggregationService(
                mock(ProfileServiceClient.class),
                mock(ProfileInternalServiceClient.class),
                mock(WalletServiceClient.class),
                mock(PromotionServiceClient.class),
                mock(PromotionInternalServiceClient.class),
                mock(MarketplaceServiceClient.class),
                mock(OrderServiceClient.class),
                mock(LoyaltyServiceClient.class),
                searchServiceClient,
                resilienceHelper,
                mock(GuestCartStore.class));
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
