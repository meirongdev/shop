package dev.meirong.shop.promotion.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.promotion.domain.PromotionOfferEntity;
import java.io.IOException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Evaluates minimum order amount conditions.
 * Conditions JSON: {"min_amount": 50.00}
 */
@Component
public class MinAmountEvaluator implements ConditionEvaluator {

    private final ObjectMapper objectMapper;

    public MinAmountEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "MIN_AMOUNT";
    }

    @Override
    public boolean evaluate(PromotionOfferEntity offer, PromotionContext context) {
        if (offer.getConditions() == null) return true;
        try {
            Map<String, Object> conds = objectMapper.readValue(
                    offer.getConditions(), new TypeReference<>() {});
            Object minAmountObj = conds.get("min_amount");
            if (minAmountObj != null) {
                BigDecimal minAmount = new BigDecimal(minAmountObj.toString());
                return context.orderAmount().compareTo(minAmount) >= 0;
            }
        } catch (IOException | NumberFormatException exception) {
            return false;
        }
        return true;
    }
}
