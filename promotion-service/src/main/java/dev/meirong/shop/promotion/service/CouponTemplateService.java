package dev.meirong.shop.promotion.service;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.api.PromotionInternalApi;
import dev.meirong.shop.promotion.domain.CouponInstanceEntity;
import dev.meirong.shop.promotion.domain.CouponInstanceRepository;
import dev.meirong.shop.promotion.domain.CouponTemplateEntity;
import dev.meirong.shop.promotion.domain.CouponTemplateRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponTemplateService {

    private static final Logger log = LoggerFactory.getLogger(CouponTemplateService.class);
    private static final long COUPON_ISSUE_LOCK_LEASE_SECONDS = 30;

    private final CouponTemplateRepository templateRepository;
    private final CouponInstanceRepository instanceRepository;
    private final RedissonClient redissonClient;

    public CouponTemplateService(CouponTemplateRepository templateRepository,
                                   CouponInstanceRepository instanceRepository,
                                   RedissonClient redissonClient) {
        this.templateRepository = templateRepository;
        this.instanceRepository = instanceRepository;
        this.redissonClient = redissonClient;
    }

    @Transactional
    public CouponTemplateEntity createTemplate(String sellerId, String code, String title,
                                                String discountType, BigDecimal discountValue,
                                                BigDecimal minOrderAmount, BigDecimal maxDiscount,
                                                int totalLimit, int perUserLimit, int validDays) {
        return templateRepository.save(new CouponTemplateEntity(
                sellerId, code, title, discountType, discountValue,
                minOrderAmount, maxDiscount, totalLimit, perUserLimit, validDays));
    }

    @Transactional(readOnly = true)
    public Optional<CouponTemplateEntity> findTemplateByCode(String code) {
        return templateRepository.findByCode(code);
    }

    @Transactional
    public CouponInstanceEntity issueToBuyer(String templateId, String buyerId) {
        return executeWithIssueLock(templateId, () -> {
            CouponTemplateEntity template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Coupon template not found"));
            if (!template.isActive()) {
                throw new BusinessException(CommonErrorCode.COUPON_INVALID, "Coupon template is inactive");
            }

            long userCount = instanceRepository.countByTemplateIdAndBuyerId(templateId, buyerId);
            if (template.getPerUserLimit() > 0 && userCount >= template.getPerUserLimit()) {
                throw new BusinessException(CommonErrorCode.COUPON_INVALID, "Per-user limit reached for this coupon");
            }

            if (template.getTotalLimit() > 0) {
                long totalIssued = instanceRepository.countByTemplateId(templateId);
                if (totalIssued >= template.getTotalLimit()) {
                    throw new BusinessException(CommonErrorCode.COUPON_INVALID, "Coupon total limit reached");
                }
            }

            Instant expiresAt = Instant.now().plus(template.getValidDays(), ChronoUnit.DAYS);
            String instanceCode = template.getCode() + "-"
                    + buyerId.substring(0, Math.min(8, buyerId.length())).toUpperCase();

            CouponInstanceEntity instance = new CouponInstanceEntity(templateId, buyerId, instanceCode, expiresAt);
            return instanceRepository.save(instance);
        });
    }

    @Transactional
    public CouponInstanceEntity issueToBuyerWithCode(String templateId, String buyerId, String code, Instant expiresAt) {
        CouponInstanceEntity instance = new CouponInstanceEntity(templateId, buyerId, code, expiresAt);
        return instanceRepository.save(instance);
    }

    public List<CouponInstanceEntity> listBuyerAvailableCoupons(String buyerId) {
        return instanceRepository.findByBuyerIdAndStatusOrderByCreatedAtDesc(buyerId, "AVAILABLE");
    }

    @Transactional(readOnly = true)
    public PromotionInternalApi.BuyerCouponsResponse listBuyerCouponSummaries(String buyerId) {
        List<CouponInstanceEntity> coupons = listBuyerAvailableCoupons(buyerId);
        var titleByTemplateId = templateRepository.findAllById(
                        coupons.stream().map(CouponInstanceEntity::getTemplateId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(CouponTemplateEntity::getId, Function.identity()));
        return new PromotionInternalApi.BuyerCouponsResponse(
                coupons.stream()
                        .map(coupon -> new PromotionInternalApi.BuyerCouponSummary(
                                coupon.getCode(),
                                titleByTemplateId.containsKey(coupon.getTemplateId())
                                        ? titleByTemplateId.get(coupon.getTemplateId()).getTitle()
                                        : coupon.getCode(),
                                coupon.getExpiresAt()))
                        .toList());
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void expireInstances() {
        List<CouponInstanceEntity> expired = instanceRepository
                .findByStatusAndExpiresAtBefore("AVAILABLE", Instant.now());
        if (expired.isEmpty()) return;
        expired.forEach(CouponInstanceEntity::markExpired);
        instanceRepository.saveAll(expired);
        log.info("Expired {} coupon instances", expired.size());
    }

    private CouponInstanceEntity executeWithIssueLock(String templateId, Supplier<CouponInstanceEntity> action) {
        RLock lock = redissonClient.getLock("shop:promotion:coupon-template:issue:" + templateId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, COUPON_ISSUE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS,
                        "Coupon template is busy, please retry: " + templateId);
            }
            return action.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                    "Interrupted while issuing coupon template: " + templateId);
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }
}
