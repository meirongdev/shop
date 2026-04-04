package dev.meirong.shop.promotion.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.promotion.domain.PromotionOfferEntity;
import dev.meirong.shop.promotion.domain.PromotionOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionEngineTest {

    @Mock
    private PromotionOfferRepository offerRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private PromotionEngine engine;

    @BeforeEach
    void setUp() {
        List<ConditionEvaluator> evaluators = List.of(
                new MinAmountEvaluator(objectMapper),
                new TimeWindowEvaluator());
        List<BenefitCalculator> calculators = List.of(
                new DiscountCalculator(objectMapper));
        engine = new PromotionEngine(offerRepository, evaluators, calculators);
    }

    @Test
    void calculate_simpleOffer_appliesDiscount() {
        PromotionOfferEntity offer = new PromotionOfferEntity(
                "SAVE10", "Save $10", "Flat $10 off", new BigDecimal("10.00"), true, "seller-1");
        when(offerRepository.findByActiveTrue()).thenReturn(List.of(offer));

        PromotionContext ctx = new PromotionContext("buyer-1", new BigDecimal("100.00"), List.of(), "SILVER", false);
        PromotionEngine.CalculationResult result = engine.calculate(ctx);

        assertEquals(new BigDecimal("100.00"), result.originalAmount());
        assertEquals(new BigDecimal("10.00"), result.totalDiscount());
        assertEquals(new BigDecimal("90.00"), result.finalAmount());
        assertEquals(1, result.appliedDiscounts().size());
    }

    @Test
    void calculate_jsonBenefits_percentageDiscount() {
        PromotionOfferEntity offer = new PromotionOfferEntity(
                "PCT15", "15% Off", "Percentage discount", BigDecimal.ZERO, true, "seller-1");
        offer.setBenefits("{\"discount_type\": \"PERCENTAGE\", \"discount_value\": 15, \"max_discount\": 50.00}");
        when(offerRepository.findByActiveTrue()).thenReturn(List.of(offer));

        PromotionContext ctx = new PromotionContext("buyer-1", new BigDecimal("200.00"), List.of(), "SILVER", false);
        PromotionEngine.CalculationResult result = engine.calculate(ctx);

        assertEquals(new BigDecimal("30.00"), result.totalDiscount());
        assertEquals(new BigDecimal("170.00"), result.finalAmount());
    }

    @Test
    void calculate_conditionNotMet_noDiscount() {
        PromotionOfferEntity offer = new PromotionOfferEntity(
                "SAVE10", "Save $10", "Min $50 order", new BigDecimal("10.00"), true, "seller-1");
        offer.setConditions("{\"min_amount\": 50.00}");
        when(offerRepository.findByActiveTrue()).thenReturn(List.of(offer));

        PromotionContext ctx = new PromotionContext("buyer-1", new BigDecimal("30.00"), List.of(), "SILVER", false);
        PromotionEngine.CalculationResult result = engine.calculate(ctx);

        assertEquals(BigDecimal.ZERO, result.totalDiscount());
        assertEquals(new BigDecimal("30.00"), result.finalAmount());
        assertTrue(result.appliedDiscounts().isEmpty());
    }

    @Test
    void calculate_exclusiveStacking_onlyFirstApplied() {
        PromotionOfferEntity offer1 = new PromotionOfferEntity(
                "VIP", "VIP Deal", "VIP exclusive", new BigDecimal("20.00"), true, "seller-1");
        offer1.setPriority(10);

        PromotionOfferEntity offer2 = new PromotionOfferEntity(
                "EXTRA5", "Extra $5", "Additive bonus", new BigDecimal("5.00"), true, "seller-1");
        offer2.setStackingPolicy("ADDITIVE");
        offer2.setPriority(5);

        when(offerRepository.findByActiveTrue()).thenReturn(List.of(offer1, offer2));

        PromotionContext ctx = new PromotionContext("buyer-1", new BigDecimal("100.00"), List.of(), "GOLD", false);
        PromotionEngine.CalculationResult result = engine.calculate(ctx);

        assertEquals(new BigDecimal("20.00"), result.totalDiscount());
        assertEquals(1, result.appliedDiscounts().size());
        assertEquals("VIP", result.appliedDiscounts().getFirst().offerCode());
    }
}
