package dev.meirong.shop.promotion.engine;

import dev.meirong.shop.promotion.domain.PromotionOfferEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Evaluates time-window conditions (start_at / end_at on the offer).
 */
@Component
public class TimeWindowEvaluator implements ConditionEvaluator {

    @Override
    public String type() {
        return "TIME_WINDOW";
    }

    @Override
    public boolean evaluate(PromotionOfferEntity offer, PromotionContext context) {
        Instant now = Instant.now();
        if (offer.getStartAt() != null && now.isBefore(offer.getStartAt())) return false;
        if (offer.getEndAt() != null && now.isAfter(offer.getEndAt())) return false;
        return true;
    }
}
