package dev.meirong.shop.loyalty.controller;

import dev.meirong.shop.common.http.TrustedHeaderNames;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.loyalty.LoyaltyApi;
import dev.meirong.shop.loyalty.domain.LoyaltyAccountEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyCheckinEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyRedemptionEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyRewardItemEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyTransactionEntity;
import dev.meirong.shop.loyalty.domain.OnboardingTaskProgressEntity;
import dev.meirong.shop.loyalty.service.CheckinService;
import dev.meirong.shop.loyalty.service.LoyaltyAccountService;
import dev.meirong.shop.loyalty.service.OnboardingTaskService;
import dev.meirong.shop.loyalty.service.RedemptionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class LoyaltyController {

    private final LoyaltyAccountService accountService;
    private final CheckinService checkinService;
    private final RedemptionService redemptionService;
    private final OnboardingTaskService onboardingTaskService;

    public LoyaltyController(LoyaltyAccountService accountService,
                             CheckinService checkinService,
                             RedemptionService redemptionService,
                             OnboardingTaskService onboardingTaskService) {
        this.accountService = accountService;
        this.checkinService = checkinService;
        this.redemptionService = redemptionService;
        this.onboardingTaskService = onboardingTaskService;
    }

    // ---- Account ----

    @GetMapping(LoyaltyApi.ACCOUNT)
    public ApiResponse<LoyaltyApi.AccountResponse> getAccount(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId) {
        LoyaltyAccountEntity account = accountService.getOrCreateAccount(buyerId);
        return ApiResponse.success(toAccountResponse(account));
    }

    @GetMapping(LoyaltyApi.TRANSACTIONS)
    public ApiResponse<Page<LoyaltyApi.TransactionResponse>> getTransactions(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<LoyaltyApi.TransactionResponse> result = accountService
                .getTransactions(buyerId, PageRequest.of(page, size))
                .map(this::toTransactionResponse);
        return ApiResponse.success(result);
    }

    // ---- Check-in ----

    @PostMapping(LoyaltyApi.CHECKIN)
    public ApiResponse<LoyaltyApi.CheckinResponse> checkin(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId) {
        LoyaltyCheckinEntity checkin = checkinService.checkin(buyerId);
        return ApiResponse.success(toCheckinResponse(checkin));
    }

    @PostMapping(LoyaltyApi.CHECKIN_MAKEUP)
    public ApiResponse<LoyaltyApi.CheckinResponse> makeupCheckin(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId,
            @Valid @RequestBody LoyaltyApi.MakeupCheckinRequest request) {
        LoyaltyCheckinEntity checkin = checkinService.makeupCheckin(buyerId, request.date());
        return ApiResponse.success(toCheckinResponse(checkin));
    }

    @GetMapping(LoyaltyApi.CHECKIN_CALENDAR)
    public ApiResponse<List<LoyaltyApi.CheckinResponse>> getCalendar(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId,
            @RequestParam int year,
            @RequestParam int month) {
        List<LoyaltyApi.CheckinResponse> result = checkinService.getCalendar(buyerId, year, month)
                .stream().map(this::toCheckinResponse).toList();
        return ApiResponse.success(result);
    }

    // ---- Rewards ----

    @GetMapping(LoyaltyApi.REWARDS)
    public ApiResponse<List<LoyaltyApi.RewardItemResponse>> listRewards() {
        List<LoyaltyApi.RewardItemResponse> items = redemptionService.listActiveRewards()
                .stream().map(this::toRewardItemResponse).toList();
        return ApiResponse.success(items);
    }

    @PostMapping(LoyaltyApi.REDEEM)
    public ApiResponse<LoyaltyApi.RedemptionResponse> redeem(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId,
            @Valid @RequestBody LoyaltyApi.RedeemRequest request) {
        LoyaltyRedemptionEntity redemption = redemptionService.redeem(
                buyerId, request.rewardItemId(), request.quantity());
        return ApiResponse.success(toRedemptionResponse(redemption));
    }

    @GetMapping(LoyaltyApi.REDEMPTIONS)
    public ApiResponse<Page<LoyaltyApi.RedemptionResponse>> getRedemptions(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<LoyaltyApi.RedemptionResponse> result = redemptionService
                .getRedemptions(buyerId, PageRequest.of(page, size))
                .map(this::toRedemptionResponse);
        return ApiResponse.success(result);
    }

    // ---- Onboarding ----

    @GetMapping(LoyaltyApi.ONBOARDING_TASKS)
    public ApiResponse<List<LoyaltyApi.OnboardingTaskResponse>> getOnboardingTasks(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId) {
        List<LoyaltyApi.OnboardingTaskResponse> tasks = onboardingTaskService.getProgress(buyerId)
                .stream().map(this::toTaskResponse).toList();
        return ApiResponse.success(tasks);
    }

    @PostMapping(LoyaltyApi.ONBOARDING_COMPLETE)
    public ApiResponse<LoyaltyApi.OnboardingTaskResponse> completeTask(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId,
            @Valid @RequestBody LoyaltyApi.CompleteTaskRequest request) {
        OnboardingTaskProgressEntity progress = onboardingTaskService.completeTask(buyerId, request.taskKey());
        return ApiResponse.success(toTaskResponse(progress));
    }

    // ---- Mappers ----

    private LoyaltyApi.AccountResponse toAccountResponse(LoyaltyAccountEntity e) {
        return new LoyaltyApi.AccountResponse(
                e.getBuyerId(), e.getTotalPoints(), e.getUsedPoints(),
                e.getBalance(), e.getTier(), e.getTierPoints());
    }

    private LoyaltyApi.TransactionResponse toTransactionResponse(LoyaltyTransactionEntity e) {
        return new LoyaltyApi.TransactionResponse(
                e.getId(), e.getType(), e.getSource(), e.getAmount(),
                e.getBalanceAfter(), e.getReferenceId(), e.getRemark(), e.getCreatedAt());
    }

    private LoyaltyApi.CheckinResponse toCheckinResponse(LoyaltyCheckinEntity e) {
        return new LoyaltyApi.CheckinResponse(
                e.getId(), e.getCheckinDate(), e.getStreakDay(),
                e.getPointsEarned(), e.isMakeup(), e.getMakeupCost());
    }

    private LoyaltyApi.RewardItemResponse toRewardItemResponse(LoyaltyRewardItemEntity e) {
        return new LoyaltyApi.RewardItemResponse(
                e.getId(), e.getName(), e.getDescription(), e.getType(),
                e.getPointsRequired(), e.getStock(), e.getImageUrl());
    }

    private LoyaltyApi.RedemptionResponse toRedemptionResponse(LoyaltyRedemptionEntity e) {
        return new LoyaltyApi.RedemptionResponse(
                e.getId(), e.getRewardName(), e.getPointsSpent(), e.getQuantity(),
                e.getStatus(), e.getType(), e.getCouponCode(), e.getCreatedAt());
    }

    private LoyaltyApi.OnboardingTaskResponse toTaskResponse(OnboardingTaskProgressEntity e) {
        return new LoyaltyApi.OnboardingTaskResponse(
                e.getTaskKey(), e.getStatus(), e.getPointsIssued(), e.getCompletedAt(), e.getExpireAt());
    }
}
