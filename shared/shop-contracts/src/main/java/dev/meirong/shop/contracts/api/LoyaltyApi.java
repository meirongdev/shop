package dev.meirong.shop.contracts.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class LoyaltyApi {

    public static final String BASE_PATH = "/loyalty/v1";
    // Account
    public static final String ACCOUNT = BASE_PATH + "/account";
    public static final String TRANSACTIONS = BASE_PATH + "/transactions";
    // Check-in
    public static final String CHECKIN = BASE_PATH + "/checkin";
    public static final String CHECKIN_MAKEUP = BASE_PATH + "/checkin/makeup";
    public static final String CHECKIN_CALENDAR = BASE_PATH + "/checkin/calendar";
    // Rewards
    public static final String REWARDS = BASE_PATH + "/rewards";
    public static final String REDEEM = BASE_PATH + "/rewards/redeem";
    public static final String REDEMPTIONS = BASE_PATH + "/redemptions";
    // Onboarding
    public static final String ONBOARDING_TASKS = BASE_PATH + "/onboarding/tasks";
    public static final String ONBOARDING_COMPLETE = BASE_PATH + "/onboarding/complete";
    // Internal
    public static final String INTERNAL_EARN = "/internal/loyalty/earn";
    public static final String INTERNAL_DEDUCT = "/internal/loyalty/deduct";
    public static final String INTERNAL_BALANCE = "/internal/loyalty/balance";

    private LoyaltyApi() {
    }

    // --- Requests ---

    public record MakeupCheckinRequest(
            @NotNull LocalDate date
    ) {}

    public record CalendarRequest(
            @Min(2000) int year,
            @Min(1) int month
    ) {}

    public record RedeemRequest(
            @NotBlank String rewardItemId,
            @Min(1) int quantity
    ) {}

    public record CompleteTaskRequest(
            @NotBlank String taskKey
    ) {}

    public record EarnPointsRequest(
            @NotBlank String buyerId,
            @NotBlank String source,
            long points,
            String referenceId,
            String remark
    ) {}

    public record DeductPointsRequest(
            @NotBlank String buyerId,
            @NotBlank String source,
            long points,
            String referenceId,
            String remark
    ) {}

    // --- Responses ---

    public record AccountResponse(
            String buyerId,
            long totalPoints,
            long usedPoints,
            long balance,
            String tier,
            long tierPoints
    ) {}

    public record TransactionResponse(
            String id,
            String type,
            String source,
            long amount,
            long balanceAfter,
            String referenceId,
            String remark,
            Instant createdAt
    ) {}

    public record CheckinResponse(
            String id,
            LocalDate checkinDate,
            int streakDay,
            long pointsEarned,
            boolean isMakeup,
            long makeupCost
    ) {}

    public record RewardItemResponse(
            String id,
            String name,
            String description,
            String type,
            long pointsRequired,
            int stock,
            String imageUrl
    ) {}

    public record RedemptionResponse(
            String id,
            String rewardName,
            long pointsSpent,
            int quantity,
            String status,
            String type,
            String couponCode,
            Instant createdAt
    ) {}

    public record OnboardingTaskResponse(
            String taskKey,
            String status,
            long pointsIssued,
            Instant completedAt,
            Instant expireAt
    ) {}
}
