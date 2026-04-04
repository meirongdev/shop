package dev.meirong.shop.promotion.engine;

import dev.meirong.shop.promotion.domain.PromotionOfferEntity;

/**
 * Evaluates whether a promotion's conditions are met.
 */
public interface ConditionEvaluator {

    String type();

    boolean evaluate(PromotionOfferEntity offer, PromotionContext context);
}
