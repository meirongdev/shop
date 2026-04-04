package dev.meirong.shop.subscription.service;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.metrics.MetricsHelper;
import dev.meirong.shop.contracts.api.SubscriptionApi;
import dev.meirong.shop.subscription.domain.SubscriptionEntity;
import dev.meirong.shop.subscription.domain.SubscriptionPlanEntity;
import dev.meirong.shop.subscription.domain.SubscriptionPlanRepository;
import dev.meirong.shop.subscription.domain.SubscriptionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionApplicationService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MetricsHelper metrics;

    public SubscriptionApplicationService(SubscriptionPlanRepository planRepository,
                                           SubscriptionRepository subscriptionRepository,
                                           MeterRegistry meterRegistry) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.metrics = new MetricsHelper("subscription-service", meterRegistry);
    }

    @Transactional
    public SubscriptionApi.PlanResponse createPlan(SubscriptionApi.CreatePlanRequest request) {
        if (!SubscriptionApi.FREQUENCIES.contains(request.frequency())) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Invalid frequency: " + request.frequency());
        }
        SubscriptionPlanEntity entity = SubscriptionPlanEntity.create(
                request.sellerId(), request.productId(), request.name(),
                request.description(), request.price(), request.frequency());
        metrics.increment("shop_subscription_plan_created_total",
                "frequency", request.frequency());
        return toPlanResponse(planRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionApi.PlanResponse> listPlans(String sellerId) {
        return planRepository.findBySellerId(sellerId).stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionApi.PlanResponse getPlan(String planId) {
        return toPlanResponse(planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                        "Plan not found: " + planId)));
    }

    @Transactional
    public SubscriptionApi.SubscriptionResponse subscribe(SubscriptionApi.SubscribeRequest request) {
        SubscriptionPlanEntity plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                        "Plan not found: " + request.planId()));
        if (!plan.isActive()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Plan is not active");
        }
        SubscriptionEntity sub = SubscriptionEntity.create(
                request.buyerId(), request.planId(), request.quantity(), plan.getFrequency());
        metrics.increment("shop_subscription_created_total",
                "frequency", plan.getFrequency());
        return toSubResponse(subscriptionRepository.save(sub));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionApi.SubscriptionResponse> listSubscriptions(String buyerId) {
        return subscriptionRepository.findByBuyerId(buyerId).stream()
                .map(this::toSubResponse)
                .toList();
    }

    @Transactional
    public SubscriptionApi.SubscriptionResponse pause(String subscriptionId) {
        SubscriptionEntity sub = findSubscription(subscriptionId);
        sub.pause();
        metrics.increment("shop_subscription_paused_total");
        return toSubResponse(subscriptionRepository.save(sub));
    }

    @Transactional
    public SubscriptionApi.SubscriptionResponse resume(String subscriptionId) {
        SubscriptionEntity sub = findSubscription(subscriptionId);
        SubscriptionPlanEntity plan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                        "Plan not found: " + sub.getPlanId()));
        sub.resume(plan.getFrequency());
        metrics.increment("shop_subscription_resumed_total");
        return toSubResponse(subscriptionRepository.save(sub));
    }

    @Transactional
    public SubscriptionApi.SubscriptionResponse cancel(String subscriptionId) {
        SubscriptionEntity sub = findSubscription(subscriptionId);
        sub.cancel();
        metrics.increment("shop_subscription_cancelled_total");
        return toSubResponse(subscriptionRepository.save(sub));
    }

    private SubscriptionEntity findSubscription(String id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                        "Subscription not found: " + id));
    }

    private SubscriptionApi.PlanResponse toPlanResponse(SubscriptionPlanEntity e) {
        return new SubscriptionApi.PlanResponse(
                e.getId(), e.getSellerId(), e.getProductId(), e.getName(),
                e.getDescription(), e.getPrice(), e.getFrequency(),
                e.isActive(), e.getCreatedAt());
    }

    private SubscriptionApi.SubscriptionResponse toSubResponse(SubscriptionEntity e) {
        return new SubscriptionApi.SubscriptionResponse(
                e.getId(), e.getBuyerId(), e.getPlanId(), e.getStatus(),
                e.getQuantity(), e.getNextRenewalAt(), e.getLastOrderId(),
                e.getTotalRenewals(), e.getCreatedAt());
    }
}
