package dev.meirong.shop.promotion.engine;

import dev.meirong.shop.promotion.domain.PromotionOfferEntity;
import java.math.BigDecimal;

/**
 * Calculates the discount/benefit for a matched promotion.
 */
public interface BenefitCalculator {

    String type();

    BigDecimal calculate(PromotionOfferEntity offer, PromotionContext context);
}
