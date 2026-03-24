package dev.meirong.shop.promotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.meirong.shop.contracts.api.PromotionApi;
import dev.meirong.shop.promotion.domain.CouponEntity;
import dev.meirong.shop.promotion.domain.CouponRepository;
import dev.meirong.shop.promotion.domain.CouponUsageRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CouponApplicationServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponUsageRepository couponUsageRepository;

    private SimpleMeterRegistry meterRegistry;
    private CouponApplicationService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new CouponApplicationService(couponRepository, couponUsageRepository, meterRegistry);
    }

    @Test
    void validateCoupon_recordsInvalidMetricForMissingCoupon() {
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.empty());

        PromotionApi.CouponValidationResponse response =
                service.validateCoupon(new PromotionApi.ValidateCouponRequest("SAVE10", BigDecimal.TEN));

        assertThat(response.valid()).isFalse();
        assertThat(meterRegistry.get("shop_coupon_validation_total")
                .tag("service", "promotion-service")
                .tag("operation", "validate")
                .tag("result", "invalid")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void validateCoupon_recordsValidMetricForApplicableCoupon() {
        CouponEntity coupon = new CouponEntity(
                "seller-1",
                "SAVE10",
                "PERCENTAGE",
                BigDecimal.TEN,
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(20),
                10,
                Instant.now().plusSeconds(3600));
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(coupon));

        PromotionApi.CouponValidationResponse response =
                service.validateCoupon(new PromotionApi.ValidateCouponRequest("SAVE10", BigDecimal.valueOf(100)));

        assertThat(response.valid()).isTrue();
        assertThat(meterRegistry.get("shop_coupon_validation_total")
                .tag("service", "promotion-service")
                .tag("operation", "validate")
                .tag("result", "valid")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
