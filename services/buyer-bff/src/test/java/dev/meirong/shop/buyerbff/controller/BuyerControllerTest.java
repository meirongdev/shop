package dev.meirong.shop.buyerbff.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.buyerbff.service.BuyerAggregationService;
import dev.meirong.shop.common.web.GlobalExceptionHandler;
import dev.meirong.shop.contracts.buyer.BuyerApi;
import dev.meirong.shop.contracts.order.OrderApi;
import dev.meirong.shop.contracts.promotion.PromotionApi;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BuyerController.class)
@Import({GlobalExceptionHandler.class, BuyerControllerTest.MetricsTestConfiguration.class})
class BuyerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private BuyerAggregationService service;

    @Test
    void wallet_withGuestRole_returnsForbidden() throws Exception {
        BuyerApi.BuyerContextRequest request = new BuyerApi.BuyerContextRequest("guest-buyer-123");

        mockMvc.perform(post(BuyerApi.WALLET)
                        .header("X-Roles", "ROLE_BUYER_GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SC_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Buyer wallet requires a signed-in buyer account"));

        verifyNoInteractions(service);
    }

    @Test
    void checkout_withGuestRole_returnsForbidden() throws Exception {
        BuyerApi.CheckoutRequest request = new BuyerApi.CheckoutRequest("guest-buyer-123", null, null, null);

        mockMvc.perform(post(BuyerApi.CHECKOUT_CREATE)
                        .header("X-Roles", "ROLE_BUYER_GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SC_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Buyer checkout requires a signed-in buyer account"));

        verifyNoInteractions(service);
    }

    @Test
    void wallet_withoutBuyerRole_returnsForbidden() throws Exception {
        BuyerApi.BuyerContextRequest request = new BuyerApi.BuyerContextRequest("buyer-1001");

        mockMvc.perform(post(BuyerApi.WALLET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SC_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Buyer wallet requires a signed-in buyer account"));

        verifyNoInteractions(service);
    }

    @Test
    void listCart_withGuestRole_stillReturnsCart() throws Exception {
        BuyerApi.BuyerContextRequest request = new BuyerApi.BuyerContextRequest("guest-buyer-123");
        OrderApi.CartItemResponse item = new OrderApi.CartItemResponse(
                UUID.randomUUID(),
                "guest-buyer-123",
                "product-1",
                "Demo Product",
                BigDecimal.valueOf(19.99),
                "seller-1",
                1,
                Instant.parse("2026-03-20T00:00:00Z")
        );
        when(service.listCart("guest-buyer-123"))
                .thenReturn(new OrderApi.CartView(List.of(item), BigDecimal.valueOf(19.99)));

        mockMvc.perform(post(BuyerApi.CART_LIST)
                        .header("X-Roles", "ROLE_BUYER_GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.items[0].buyerId").value("guest-buyer-123"))
                .andExpect(jsonPath("$.data.subtotal").value(19.99));
    }

    @Test
    void addToCart_prefersAuthenticatedHeaderPlayerId() throws Exception {
        OrderApi.AddToCartRequest request = new OrderApi.AddToCartRequest(
                "guest-buyer-123",
                "product-1",
                "Demo Product",
                BigDecimal.valueOf(19.99),
                "seller-1",
                2
        );
        OrderApi.CartItemResponse response = new OrderApi.CartItemResponse(
                UUID.randomUUID(),
                "buyer-1001",
                "product-1",
                "Demo Product",
                BigDecimal.valueOf(19.99),
                "seller-1",
                2,
                Instant.parse("2026-03-20T00:00:00Z")
        );
        when(service.addToCart(argThat(cartRequest -> "buyer-1001".equals(cartRequest.buyerId()))))
                .thenReturn(response);

        mockMvc.perform(post(BuyerApi.CART_ADD)
                        .header("X-Buyer-Id", "buyer-1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.buyerId").value("buyer-1001"))
                .andExpect(jsonPath("$.data.quantity").value(2));

        verify(service).addToCart(argThat(cartRequest ->
                "buyer-1001".equals(cartRequest.buyerId()) && "product-1".equals(cartRequest.productId())));
    }

    @Test
    void mergeCart_withGuestRole_returnsForbidden() throws Exception {
        BuyerApi.MergeGuestCartRequest request = new BuyerApi.MergeGuestCartRequest("guest-buyer-123");

        mockMvc.perform(post(BuyerApi.CART_MERGE)
                        .header("X-Roles", "ROLE_BUYER_GUEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SC_FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Buyer cart merge requires a signed-in buyer account"));

        verifyNoInteractions(service);
    }

    @Test
    void wallet_withBuyerRole_returnsWallet() throws Exception {
        BuyerApi.BuyerContextRequest request = new BuyerApi.BuyerContextRequest("buyer-1001");
        when(service.getWallet("buyer-1001")).thenReturn(new dev.meirong.shop.contracts.wallet.WalletApi.WalletAccountResponse(
                "buyer-1001",
                BigDecimal.valueOf(25.00),
                Instant.parse("2026-03-20T00:00:00Z"),
                List.of()
        ));

        mockMvc.perform(post(BuyerApi.WALLET)
                        .header("X-Roles", "ROLE_BUYER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data.buyerId").value("buyer-1001"))
                .andExpect(jsonPath("$.data.balance").value(25.00));
    }

    @Test
    void listCoupons_withGuestRole_returnsCoupons() throws Exception {
        PromotionApi.CouponResponse coupon = new PromotionApi.CouponResponse(
                UUID.randomUUID(),
                "seller-2001",
                "SAVE10",
                "PERCENTAGE",
                BigDecimal.TEN,
                BigDecimal.valueOf(50.00),
                BigDecimal.valueOf(20.00),
                100,
                3,
                Instant.parse("2026-12-31T00:00:00Z"),
                true,
                Instant.parse("2026-03-20T00:00:00Z")
        );
        when(service.listCoupons()).thenReturn(List.of(coupon));

        mockMvc.perform(post(BuyerApi.COUPON_LIST)
                        .header("X-Roles", "ROLE_BUYER_GUEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SC_OK"))
                .andExpect(jsonPath("$.data[0].code").value("SAVE10"))
                .andExpect(jsonPath("$.data[0].discountType").value("PERCENTAGE"));
    }

    @Test
    void checkout_recordsDurationMetric() throws Exception {
        BuyerApi.CheckoutRequest request = new BuyerApi.CheckoutRequest("buyer-1001", null, "APPLE_PAY", null);
        when(service.checkout(argThat(checkoutRequest -> "buyer-1001".equals(checkoutRequest.buyerId()))))
                .thenReturn(new BuyerApi.CheckoutResponse(List.of(), BigDecimal.ZERO, "APPLE_PAY", "secret", null));

        mockMvc.perform(post(BuyerApi.CHECKOUT_CREATE)
                        .header("X-Buyer-Id", "buyer-1001")
                        .header("X-Roles", "ROLE_BUYER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMethod").value("APPLE_PAY"));

        assertThat(meterRegistry.get("shop_order_checkout_duration_seconds")
                .tag("service", "buyer-bff")
                .tag("operation", "checkout")
                .tag("provider", "apple_pay")
                .tag("result", "success")
                .timer()
                .count()).isEqualTo(1);
    }

    @TestConfiguration
    static class MetricsTestConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
