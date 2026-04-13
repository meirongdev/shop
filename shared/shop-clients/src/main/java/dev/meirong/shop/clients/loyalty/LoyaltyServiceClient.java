package dev.meirong.shop.clients.loyalty;

import dev.meirong.shop.clients.PageResponse;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.http.TrustedHeaderNames;
import dev.meirong.shop.contracts.loyalty.LoyaltyApi;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * {@code @HttpExchange} client for loyalty-service.
 * Primarily used by buyer-bff.
 */
@HttpExchange
public interface LoyaltyServiceClient {

    @GetExchange(LoyaltyApi.INTERNAL_BALANCE + "/{buyerId}")
    ApiResponse<LoyaltyApi.AccountResponse> getAccount(@PathVariable String buyerId);

    @PostExchange(LoyaltyApi.CHECKIN)
    ApiResponse<LoyaltyApi.CheckinResponse> checkin(@RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId);

    @GetExchange(LoyaltyApi.CHECKIN_CALENDAR)
    ApiResponse<List<LoyaltyApi.CheckinResponse>> getCheckinCalendar(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId,
            @RequestParam("year") int year,
            @RequestParam("month") int month);

    @GetExchange(LoyaltyApi.TRANSACTIONS)
    ApiResponse<PageResponse<LoyaltyApi.TransactionResponse>> getTransactions(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId,
            @RequestParam("page") int page,
            @RequestParam("size") int size);

    @GetExchange(LoyaltyApi.REWARDS)
    ApiResponse<List<LoyaltyApi.RewardItemResponse>> listRewards();

    @PostExchange(LoyaltyApi.REDEEM)
    ApiResponse<LoyaltyApi.RedemptionResponse> redeemReward(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId,
            @RequestBody LoyaltyApi.RedeemRequest request);

    @GetExchange(LoyaltyApi.REDEMPTIONS)
    ApiResponse<PageResponse<LoyaltyApi.RedemptionResponse>> getRedemptions(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId,
            @RequestParam("page") int page,
            @RequestParam("size") int size);

    @GetExchange(LoyaltyApi.ONBOARDING_TASKS)
    ApiResponse<List<LoyaltyApi.OnboardingTaskResponse>> getOnboardingTasks(
            @RequestHeader(TrustedHeaderNames.BUYER_ID) String buyerId);

    @PostExchange(LoyaltyApi.INTERNAL_DEDUCT)
    ApiResponse<LoyaltyApi.TransactionResponse> deductPoints(@RequestBody LoyaltyApi.DeductPointsRequest request);

    @PostExchange(LoyaltyApi.INTERNAL_EARN)
    ApiResponse<LoyaltyApi.TransactionResponse> earnPoints(@RequestBody LoyaltyApi.EarnPointsRequest request);
}
