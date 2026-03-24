package dev.meirong.shop.promotion.engine;

import dev.meirong.shop.promotion.domain.PromotionOfferEntity;
import dev.meirong.shop.promotion.domain.PromotionOfferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Core promotion engine: evaluates all active offers against cart context,
 * applies stacking policy, and returns the best discount breakdown.
 */
@Service
public class PromotionEngine {

    private static final Logger log = LoggerFactory.getLogger(PromotionEngine.class);

    private final PromotionOfferRepository offerRepository;
    private final List<ConditionEvaluator> evaluators;
    private final List<BenefitCalculator> calculators;

    public PromotionEngine(PromotionOfferRepository offerRepository,
                           List<ConditionEvaluator> evaluators,
                           List<BenefitCalculator> calculators) {
        this.offerRepository = offerRepository;
        this.evaluators = evaluators;
        this.calculators = calculators;
    }

    public CalculationResult calculate(PromotionContext context) {
        List<PromotionOfferEntity> offers = new ArrayList<>(offerRepository.findByActiveTrue());

        // Sort by priority (higher first)
        offers.sort(Comparator.comparingInt(PromotionOfferEntity::getPriority).reversed());

        List<DiscountLine> lines = new ArrayList<>();
        BigDecimal totalDiscount = BigDecimal.ZERO;
        boolean hasExclusive = false;

        for (PromotionOfferEntity offer : offers) {
            if (hasExclusive) break;

            // Evaluate all conditions
            boolean conditionsMet = evaluators.stream()
                    .allMatch(e -> e.evaluate(offer, context));
            if (!conditionsMet) continue;

            // Calculate benefit using the first matching calculator (DISCOUNT for now)
            BigDecimal discount = calculators.stream()
                    .findFirst()
                    .map(c -> c.calculate(offer, context))
                    .orElse(BigDecimal.ZERO);

            if (discount.compareTo(BigDecimal.ZERO) > 0) {
                lines.add(new DiscountLine(offer.getId(), offer.getCode(), offer.getTitle(), discount));
                totalDiscount = totalDiscount.add(discount);

                if ("EXCLUSIVE".equals(offer.getStackingPolicy())) {
                    hasExclusive = true;
                }
            }
        }

        // Cap total discount at order amount
        if (totalDiscount.compareTo(context.orderAmount()) > 0) {
            totalDiscount = context.orderAmount();
        }

        BigDecimal finalAmount = context.orderAmount().subtract(totalDiscount);

        log.debug("Promotion calculation: orderAmount={}, totalDiscount={}, appliedOffers={}",
                context.orderAmount(), totalDiscount, lines.size());

        return new CalculationResult(context.orderAmount(), totalDiscount, finalAmount, lines);
    }

    public record DiscountLine(String offerId, String offerCode, String offerTitle, BigDecimal discount) {}

    public record CalculationResult(
            BigDecimal originalAmount,
            BigDecimal totalDiscount,
            BigDecimal finalAmount,
            List<DiscountLine> appliedDiscounts
    ) {}
}
