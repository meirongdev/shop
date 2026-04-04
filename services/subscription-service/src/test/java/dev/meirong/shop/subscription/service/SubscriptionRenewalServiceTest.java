package dev.meirong.shop.subscription.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.meirong.shop.subscription.domain.SubscriptionEntity;
import dev.meirong.shop.subscription.domain.SubscriptionOrderLogRepository;
import dev.meirong.shop.subscription.domain.SubscriptionPlanEntity;
import dev.meirong.shop.subscription.domain.SubscriptionPlanRepository;
import dev.meirong.shop.subscription.domain.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class SubscriptionRenewalServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionPlanRepository planRepository;

    @Mock
    private SubscriptionOrderLogRepository orderLogRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock renewalLock;

    private SubscriptionRenewalService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new SubscriptionRenewalService(
                subscriptionRepository,
                planRepository,
                orderLogRepository,
                redissonClient
        );
        when(redissonClient.getLock("shop:subscription:scheduler:renewal")).thenReturn(renewalLock);
        when(renewalLock.tryLock(0, 1800, TimeUnit.SECONDS)).thenReturn(true);
    }

    @Test
    void processRenewals_withLock_renewsDueSubscriptions() {
        SubscriptionEntity subscription = SubscriptionEntity.create("buyer-1", "plan-1", 1, "MONTHLY");
        SubscriptionPlanEntity plan = SubscriptionPlanEntity.create(
                "seller-1", "product-1", "Monthly", null, new BigDecimal("29.99"), "MONTHLY");
        when(subscriptionRepository.findByStatusAndNextRenewalAtBefore(any(), any()))
                .thenReturn(List.of(subscription));
        when(planRepository.findById(subscription.getPlanId())).thenReturn(Optional.of(plan));

        service.processRenewals();

        verify(orderLogRepository).save(any());
        verify(subscriptionRepository).save(subscription);
        verify(renewalLock).unlock();
    }

    @Test
    void processRenewals_whenLockBusy_skipsBatch() throws Exception {
        when(renewalLock.tryLock(0, 1800, TimeUnit.SECONDS)).thenReturn(false);

        service.processRenewals();

        verify(subscriptionRepository, never()).findByStatusAndNextRenewalAtBefore(any(), any(Instant.class));
        verify(renewalLock, never()).unlock();
    }
}
