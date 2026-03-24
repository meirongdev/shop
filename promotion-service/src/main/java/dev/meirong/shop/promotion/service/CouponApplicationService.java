package dev.meirong.shop.promotion.service;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.api.PromotionApi;
import dev.meirong.shop.promotion.domain.CouponEntity;
import dev.meirong.shop.promotion.domain.CouponRepository;
import dev.meirong.shop.promotion.domain.CouponUsageEntity;
import dev.meirong.shop.promotion.domain.CouponUsageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponApplicationService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final MeterRegistry meterRegistry;

    public CouponApplicationService(CouponRepository couponRepository,
                                    CouponUsageRepository couponUsageRepository,
                                    MeterRegistry meterRegistry) {
        this.couponRepository = couponRepository;
        this.couponUsageRepository = couponUsageRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public PromotionApi.CouponResponse createCoupon(PromotionApi.CreateCouponRequest request) {
        CouponEntity entity = new CouponEntity(
                request.sellerId(), request.code(), request.discountType(), request.discountValue(),
                request.minOrderAmount(), request.maxDiscount(),
                request.usageLimit() != null ? request.usageLimit() : 0,
                request.expiresAt());
        return toResponse(couponRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<PromotionApi.CouponResponse> listCoupons(String sellerId) {
        List<CouponEntity> coupons = (sellerId != null && !sellerId.isBlank())
                ? couponRepository.findBySellerIdOrderByCreatedAtDesc(sellerId)
                : couponRepository.findByActiveTrueOrderByCreatedAtDesc();
        return coupons.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PromotionApi.CouponValidationResponse validateCoupon(PromotionApi.ValidateCouponRequest request) {
        String result = "failure";
        try {
            CouponEntity coupon = couponRepository.findByCode(request.code()).orElse(null);
            if (coupon == null || !coupon.isActive()) {
                result = "invalid";
                return new PromotionApi.CouponValidationResponse(false, null, request.code(),
                        null, null, BigDecimal.ZERO, "Coupon not found or inactive");
            }
            if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(Instant.now())) {
                result = "invalid";
                return new PromotionApi.CouponValidationResponse(false, coupon.getId(), coupon.getCode(),
                        coupon.getDiscountType(), coupon.getDiscountValue(), BigDecimal.ZERO, "Coupon has expired");
            }
            if (coupon.getUsageLimit() > 0 && coupon.getUsedCount() >= coupon.getUsageLimit()) {
                result = "invalid";
                return new PromotionApi.CouponValidationResponse(false, coupon.getId(), coupon.getCode(),
                        coupon.getDiscountType(), coupon.getDiscountValue(), BigDecimal.ZERO, "Coupon usage limit reached");
            }
            if (request.orderAmount().compareTo(coupon.getMinOrderAmount()) < 0) {
                result = "invalid";
                return new PromotionApi.CouponValidationResponse(false, coupon.getId(), coupon.getCode(),
                        coupon.getDiscountType(), coupon.getDiscountValue(), BigDecimal.ZERO,
                        "Minimum order amount is $" + coupon.getMinOrderAmount());
            }
            BigDecimal discount = calculateDiscount(coupon, request.orderAmount());
            result = "valid";
            return new PromotionApi.CouponValidationResponse(true, coupon.getId(), coupon.getCode(),
                    coupon.getDiscountType(), coupon.getDiscountValue(), discount, "Coupon is valid");
        } finally {
            validationCounter(result).increment();
        }
    }

    @Transactional
    public void applyCoupon(PromotionApi.ApplyCouponRequest request) {
        CouponEntity coupon = couponRepository.findById(request.couponId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.COUPON_INVALID, "Coupon not found"));
        coupon.incrementUsedCount();
        couponRepository.save(coupon);
        couponUsageRepository.save(new CouponUsageEntity(
                request.couponId(), request.buyerId(), request.orderId(), request.discountApplied()));
    }

    private BigDecimal calculateDiscount(CouponEntity coupon, BigDecimal orderAmount) {
        BigDecimal discount;
        if ("PERCENTAGE".equals(coupon.getDiscountType())) {
            discount = orderAmount.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
                discount = coupon.getMaxDiscount();
            }
        } else {
            discount = coupon.getDiscountValue();
        }
        return discount.min(orderAmount);
    }

    private PromotionApi.CouponResponse toResponse(CouponEntity entity) {
        return new PromotionApi.CouponResponse(
                UUID.fromString(entity.getId()),
                entity.getSellerId(),
                entity.getCode(),
                entity.getDiscountType(),
                entity.getDiscountValue(),
                entity.getMinOrderAmount(),
                entity.getMaxDiscount(),
                entity.getUsageLimit(),
                entity.getUsedCount(),
                entity.getExpiresAt(),
                entity.isActive(),
                entity.getCreatedAt());
    }

    private Counter validationCounter(String result) {
        return Counter.builder("shop_coupon_validation_total")
                .tag("service", "promotion-service")
                .tag("operation", "validate")
                .tag("result", result)
                .register(meterRegistry);
    }
}
