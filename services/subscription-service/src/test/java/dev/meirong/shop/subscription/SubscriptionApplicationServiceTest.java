package dev.meirong.shop.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.contracts.api.SubscriptionApi;
import dev.meirong.shop.subscription.domain.SubscriptionPlanEntity;
import dev.meirong.shop.subscription.domain.SubscriptionPlanRepository;
import dev.meirong.shop.subscription.domain.SubscriptionRepository;
import dev.meirong.shop.subscription.service.SubscriptionApplicationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.micrometer.core.instrument.MeterRegistry;

@ExtendWith(MockitoExtension.class)
class SubscriptionApplicationServiceTest {

    @Mock
    private SubscriptionPlanRepository planRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry meterRegistry;

    @InjectMocks
    private SubscriptionApplicationService service;

    @Test
    void createPlan_savesAndReturnsResponse() {
        SubscriptionApi.CreatePlanRequest request = new SubscriptionApi.CreatePlanRequest(
                "seller-1", "product-1", "Monthly Coffee",
                "Fresh coffee delivered monthly", new BigDecimal("29.99"), "MONTHLY");
        when(planRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SubscriptionApi.PlanResponse response = service.createPlan(request);

        assertThat(response.sellerId()).isEqualTo("seller-1");
        assertThat(response.name()).isEqualTo("Monthly Coffee");
        assertThat(response.price()).isEqualByComparingTo(new BigDecimal("29.99"));
        assertThat(response.frequency()).isEqualTo("MONTHLY");
        assertThat(response.active()).isTrue();
    }

    @Test
    void createPlan_invalidFrequency_throws() {
        SubscriptionApi.CreatePlanRequest request = new SubscriptionApi.CreatePlanRequest(
                "seller-1", "product-1", "Invalid", null,
                new BigDecimal("10"), "DAILY");

        assertThatThrownBy(() -> service.createPlan(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid frequency");
    }

    @Test
    void subscribe_createsActiveSubscription() {
        SubscriptionPlanEntity plan = SubscriptionPlanEntity.create(
                "seller-1", "product-1", "Monthly", null,
                new BigDecimal("29.99"), "MONTHLY");
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(subscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SubscriptionApi.SubscriptionResponse response = service.subscribe(
                new SubscriptionApi.SubscribeRequest("buyer-1", plan.getId(), 2));

        assertThat(response.buyerId()).isEqualTo("buyer-1");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.nextRenewalAt()).isNotNull();
    }

    @Test
    void subscribe_inactivePlan_throws() {
        SubscriptionPlanEntity plan = SubscriptionPlanEntity.create(
                "seller-1", "product-1", "Monthly", null,
                new BigDecimal("29.99"), "MONTHLY");
        plan.deactivate();
        when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.subscribe(
                new SubscriptionApi.SubscribeRequest("buyer-1", plan.getId(), 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Plan is not active");
    }

    @Test
    void listPlans_returnsSellerPlans() {
        SubscriptionPlanEntity plan = SubscriptionPlanEntity.create(
                "seller-1", "product-1", "Weekly Box", null,
                new BigDecimal("19.99"), "WEEKLY");
        when(planRepository.findBySellerId("seller-1")).thenReturn(List.of(plan));

        List<SubscriptionApi.PlanResponse> results = service.listPlans("seller-1");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).frequency()).isEqualTo("WEEKLY");
    }
}
