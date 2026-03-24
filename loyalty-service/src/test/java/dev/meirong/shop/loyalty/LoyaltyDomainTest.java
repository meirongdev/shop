package dev.meirong.shop.loyalty;

import dev.meirong.shop.loyalty.domain.LoyaltyAccountEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyCheckinEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyRedemptionEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyRewardItemEntity;
import dev.meirong.shop.loyalty.domain.OnboardingTaskProgressEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class LoyaltyDomainTest {

    @Nested
    @DisplayName("LoyaltyAccountEntity")
    class AccountTest {

        @Test
        void earnPoints_increasesBalanceAndTotals() {
            LoyaltyAccountEntity account = new LoyaltyAccountEntity("player-1");
            account.earnPoints(100);

            assertEquals(100, account.getBalance());
            assertEquals(100, account.getTotalPoints());
            assertEquals(0, account.getUsedPoints());
            assertEquals("SILVER", account.getTier());
        }

        @Test
        void deductPoints_decreasesBalance() {
            LoyaltyAccountEntity account = new LoyaltyAccountEntity("player-1");
            account.earnPoints(200);
            account.deductPoints(50);

            assertEquals(150, account.getBalance());
            assertEquals(50, account.getUsedPoints());
            assertEquals(200, account.getTotalPoints());
        }

        @Test
        void deductPoints_throwsOnInsufficientBalance() {
            LoyaltyAccountEntity account = new LoyaltyAccountEntity("player-1");
            account.earnPoints(10);

            assertThrows(IllegalStateException.class, () -> account.deductPoints(20));
        }

        @Test
        void tierUpgrade_gold() {
            LoyaltyAccountEntity account = new LoyaltyAccountEntity("player-1");
            account.earnPoints(3000);
            assertEquals("GOLD", account.getTier());
        }

        @Test
        void tierUpgrade_platinum() {
            LoyaltyAccountEntity account = new LoyaltyAccountEntity("player-1");
            account.earnPoints(10000);
            assertEquals("PLATINUM", account.getTier());
        }
    }

    @Nested
    @DisplayName("LoyaltyRedemptionEntity")
    class RedemptionTest {

        @Test
        void markCompleted_setsStatusAndCoupon() {
            LoyaltyRedemptionEntity redemption = new LoyaltyRedemptionEntity(
                    "player-1", "reward-1", "Test Coupon", 100, 1, "COUPON");

            redemption.markCompleted("ABC-123", null);

            assertEquals("COMPLETED", redemption.getStatus());
            assertEquals("ABC-123", redemption.getCouponCode());
        }

        @Test
        void markFailed_setsStatusAndRemark() {
            LoyaltyRedemptionEntity redemption = new LoyaltyRedemptionEntity(
                    "player-1", "reward-1", "Test Item", 500, 1, "VIRTUAL");

            redemption.markFailed("Out of stock");

            assertEquals("FAILED", redemption.getStatus());
            assertEquals("Out of stock", redemption.getRemark());
        }
    }

    @Nested
    @DisplayName("OnboardingTaskProgressEntity")
    class OnboardingTest {

        @Test
        void init_createsPendingTask() {
            Instant expire = Instant.now().plus(30, ChronoUnit.DAYS);
            OnboardingTaskProgressEntity progress =
                    OnboardingTaskProgressEntity.init("player-1", "FIRST_ORDER", expire);

            assertEquals("PENDING", progress.getStatus());
            assertEquals("player-1", progress.getBuyerId());
            assertEquals("FIRST_ORDER", progress.getTaskKey());
            assertFalse(progress.isCompleted());
        }

        @Test
        void complete_setsStatusAndPoints() {
            Instant expire = Instant.now().plus(30, ChronoUnit.DAYS);
            OnboardingTaskProgressEntity progress =
                    OnboardingTaskProgressEntity.init("player-1", "FIRST_ORDER", expire);

            progress.complete(50);

            assertTrue(progress.isCompleted());
            assertEquals(50, progress.getPointsIssued());
            assertNotNull(progress.getCompletedAt());
        }

        @Test
        void isExpired_whenPastExpiry() {
            Instant pastExpire = Instant.now().minus(1, ChronoUnit.DAYS);
            OnboardingTaskProgressEntity progress =
                    OnboardingTaskProgressEntity.init("player-1", "FIRST_ORDER", pastExpire);

            assertTrue(progress.isExpired());
        }
    }

    @Nested
    @DisplayName("LoyaltyCheckinEntity")
    class CheckinTest {

        @Test
        void constructor_setsFields() {
            LoyaltyCheckinEntity checkin = new LoyaltyCheckinEntity(
                    "player-1", LocalDate.of(2026, 1, 15), 3, 10, false, 0);

            assertEquals("player-1", checkin.getBuyerId());
            assertEquals(LocalDate.of(2026, 1, 15), checkin.getCheckinDate());
            assertEquals(3, checkin.getStreakDay());
            assertEquals(10, checkin.getPointsEarned());
            assertFalse(checkin.isMakeup());
        }

        @Test
        void makeupCheckin_costRecorded() {
            LoyaltyCheckinEntity checkin = new LoyaltyCheckinEntity(
                    "player-1", LocalDate.of(2026, 1, 14), 1, 5, true, 20);

            assertTrue(checkin.isMakeup());
            assertEquals(20, checkin.getMakeupCost());
        }
    }
}
