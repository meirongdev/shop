package dev.meirong.shop.marketplace.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProductEnhancementsTest {

    // --- Review Entity tests ---

    @Test
    void review_creation() {
        ProductReviewEntity review = new ProductReviewEntity(
                "prod-1", "buyer-1", "order-1", 5, "Great product!", "[\"img1.jpg\"]");
        assertEquals("prod-1", review.getProductId());
        assertEquals("buyer-1", review.getBuyerId());
        assertEquals("order-1", review.getOrderId());
        assertEquals(5, review.getRating());
        assertEquals("Great product!", review.getContent());
        assertEquals("APPROVED", review.getStatus());
    }

    @Test
    void review_status_change() {
        ProductReviewEntity review = new ProductReviewEntity(
                "prod-1", "buyer-1", null, 3, "OK", null);
        assertEquals("APPROVED", review.getStatus());
        review.setStatus("REJECTED");
        assertEquals("REJECTED", review.getStatus());
    }

    // --- Variant Entity tests ---

    @Test
    void variant_creation() {
        ProductVariantEntity variant = new ProductVariantEntity(
                "prod-1", "Red / Large",
                "{\"color\":\"Red\",\"size\":\"Large\"}",
                BigDecimal.valueOf(5.00), 10, "RED-L");
        assertEquals("prod-1", variant.getProductId());
        assertEquals("Red / Large", variant.getVariantName());
        assertEquals(10, variant.getInventory());
        assertEquals(BigDecimal.valueOf(5.00), variant.getPriceAdjust());
    }

    @Test
    void variant_deductInventory() {
        ProductVariantEntity variant = new ProductVariantEntity(
                "prod-1", "Blue / Small",
                "{\"color\":\"Blue\",\"size\":\"Small\"}",
                BigDecimal.ZERO, 3, "BLU-S");
        assertTrue(variant.deductInventory(2));
        assertEquals(1, variant.getInventory());
        assertTrue(variant.deductInventory(1));
        assertEquals(0, variant.getInventory());
        assertFalse(variant.deductInventory(1));
    }

    @Test
    void variant_restoreInventory() {
        ProductVariantEntity variant = new ProductVariantEntity(
                "prod-1", "Green / Medium",
                "{\"color\":\"Green\",\"size\":\"Medium\"}",
                BigDecimal.ZERO, 0, "GRN-M");
        variant.restoreInventory(5);
        assertEquals(5, variant.getInventory());
    }

    // --- Product review stats tests ---

    @Test
    void product_updateReviewStats() {
        MarketplaceProductEntity product = new MarketplaceProductEntity(
                "seller-1", "SKU-001", "Test", "Desc",
                BigDecimal.TEN, 100, true);
        assertEquals(0, product.getReviewCount());
        assertEquals(BigDecimal.ZERO, product.getAvgRating());

        product.updateReviewStats(10, BigDecimal.valueOf(4.5));
        assertEquals(10, product.getReviewCount());
        assertEquals(BigDecimal.valueOf(4.5), product.getAvgRating());
    }
}
