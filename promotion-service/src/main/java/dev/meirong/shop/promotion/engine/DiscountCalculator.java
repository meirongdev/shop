package dev.meirong.shop.promotion.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.promotion.domain.PromotionOfferEntity;
import java.io.IOException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Calculates discount based on benefits JSON.
 * Benefits JSON: {"discount_type": "FIXED", "discount_value": 10.00}
 *            or: {"discount_type": "PERCENTAGE", "discount_value": 15, "max_discount": 50.00}
 * Falls back to offer.rewardAmount for SIMPLE type offers.
 */
@Component
public class DiscountCalculator implements BenefitCalculator {

    private final ObjectMapper objectMapper;

    public DiscountCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "DISCOUNT";
    }

    @Override
    public BigDecimal calculate(PromotionOfferEntity offer, PromotionContext context) {
        if (offer.getBenefits() == null) {
            // Fallback: use rewardAmount for simple offers
            return offer.getRewardAmount().min(context.orderAmount());
        }

        try {
            Map<String, Object> bens = objectMapper.readValue(
                    offer.getBenefits(), new TypeReference<>() {});
            String discountType = (String) bens.getOrDefault("discount_type", "FIXED");
            BigDecimal discountValue = new BigDecimal(bens.get("discount_value").toString());

            BigDecimal discount;
            if ("PERCENTAGE".equals(discountType)) {
                discount = context.orderAmount()
                        .multiply(discountValue)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                Object maxDiscount = bens.get("max_discount");
                if (maxDiscount != null) {
                    BigDecimal cap = new BigDecimal(maxDiscount.toString());
                    discount = discount.min(cap);
                }
            } else {
                discount = discountValue;
            }

            return discount.min(context.orderAmount());
        } catch (IOException | NumberFormatException exception) {
            return offer.getRewardAmount().min(context.orderAmount());
        }
    }
}
