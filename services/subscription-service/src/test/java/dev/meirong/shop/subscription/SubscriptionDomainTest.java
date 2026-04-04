package dev.meirong.shop.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meirong.shop.subscription.domain.SubscriptionEntity;
import dev.meirong.shop.subscription.domain.SubscriptionPlanEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class SubscriptionDomainTest {

    @Test
    void subscription_create_setsActiveAndNextRenewal() {
        SubscriptionEntity sub = SubscriptionEntity.create("buyer-1", "plan-1", 1, "MONTHLY");

        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
        assertThat(sub.getNextRenewalAt()).isAfter(Instant.now().plus(29, ChronoUnit.DAYS));
        assertThat(sub.getTotalRenewals()).isZero();
    }

    @Test
    void subscription_pause_and_resume() {
        SubscriptionEntity sub = SubscriptionEntity.create("buyer-1", "plan-1", 1, "WEEKLY");
        sub.pause();
        assertThat(sub.getStatus()).isEqualTo("PAUSED");

        sub.resume("WEEKLY");
        assertThat(sub.getStatus()).isEqualTo("ACTIVE");
        assertThat(sub.getNextRenewalAt()).isNotNull();
    }

    @Test
    void subscription_cancel_clearsRenewal() {
        SubscriptionEntity sub = SubscriptionEntity.create("buyer-1", "plan-1", 1, "MONTHLY");
        sub.cancel();

        assertThat(sub.getStatus()).isEqualTo("CANCELLED");
        assertThat(sub.getNextRenewalAt()).isNull();
    }

    @Test
    void subscription_recordRenewal_incrementsCounter() {
        SubscriptionEntity sub = SubscriptionEntity.create("buyer-1", "plan-1", 1, "BIWEEKLY");
        sub.recordRenewal("order-123", "BIWEEKLY");

        assertThat(sub.getTotalRenewals()).isEqualTo(1);
        assertThat(sub.getLastOrderId()).isEqualTo("order-123");
    }

    @Test
    void subscription_isDueForRenewal() {
        SubscriptionEntity sub = SubscriptionEntity.create("buyer-1", "plan-1", 1, "WEEKLY");
        // New subscription is NOT due yet
        assertThat(sub.isDueForRenewal()).isFalse();
    }

    @Test
    void plan_create_isActive() {
        SubscriptionPlanEntity plan = SubscriptionPlanEntity.create(
                "seller-1", "prod-1", "Monthly", "desc",
                new BigDecimal("25.00"), "MONTHLY");

        assertThat(plan.isActive()).isTrue();
        assertThat(plan.getFrequency()).isEqualTo("MONTHLY");
    }

    @Test
    void plan_deactivate() {
        SubscriptionPlanEntity plan = SubscriptionPlanEntity.create(
                "seller-1", "prod-1", "Weekly", null,
                new BigDecimal("10.00"), "WEEKLY");
        plan.deactivate();
        assertThat(plan.isActive()).isFalse();

        plan.activate();
        assertThat(plan.isActive()).isTrue();
    }

    @Test
    void computeNextRenewal_weekly_viaPauseResume() {
        SubscriptionEntity sub = SubscriptionEntity.create("buyer-1", "plan-1", 1, "WEEKLY");
        Instant initialRenewal = sub.getNextRenewalAt();
        assertThat(initialRenewal).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
        assertThat(initialRenewal).isBefore(Instant.now().plus(8, ChronoUnit.DAYS));
    }

    @Test
    void computeNextRenewal_quarterly_viaPauseResume() {
        SubscriptionEntity sub = SubscriptionEntity.create("buyer-1", "plan-1", 1, "QUARTERLY");
        Instant renewal = sub.getNextRenewalAt();
        assertThat(renewal).isAfter(Instant.now().plus(89, ChronoUnit.DAYS));
        assertThat(renewal).isBefore(Instant.now().plus(91, ChronoUnit.DAYS));
    }
}
