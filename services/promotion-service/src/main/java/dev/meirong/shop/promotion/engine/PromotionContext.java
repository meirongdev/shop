package dev.meirong.shop.promotion.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Context for evaluating promotions against a cart.
 */
public record PromotionContext(
        String buyerId,
        BigDecimal orderAmount,
        List<CartItem> items,
        String userTier,
        boolean isNewUser
) {
    public record CartItem(
            String productId,
            String categoryId,
            BigDecimal price,
            int quantity
    ) {}
}
