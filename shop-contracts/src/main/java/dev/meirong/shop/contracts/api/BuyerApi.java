package dev.meirong.shop.contracts.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class BuyerApi {

    public static final String BASE_PATH = "/buyer/v1";
    public static final String DASHBOARD = BASE_PATH + "/dashboard/get";
    public static final String PROFILE = BASE_PATH + "/profile/get";
    public static final String PROFILE_UPDATE = BASE_PATH + "/profile/update";
    public static final String WALLET = BASE_PATH + "/wallet/get";
    public static final String DEPOSIT = BASE_PATH + "/wallet/deposit";
    public static final String WITHDRAW = BASE_PATH + "/wallet/withdraw";
    public static final String PROMOTIONS = BASE_PATH + "/promotion/list";
    public static final String COUPON_LIST = BASE_PATH + "/coupon/list";
    public static final String MARKETPLACE = BASE_PATH + "/marketplace/list";

    // New e-commerce paths
    public static final String CART_LIST = BASE_PATH + "/cart/list";
    public static final String CART_ADD = BASE_PATH + "/cart/add";
    public static final String CART_UPDATE = BASE_PATH + "/cart/update";
    public static final String CART_REMOVE = BASE_PATH + "/cart/remove";
    public static final String CART_MERGE = BASE_PATH + "/cart/merge";
    public static final String PRODUCT_GET = BASE_PATH + "/product/get";
    public static final String PRODUCT_SEARCH = BASE_PATH + "/product/search";
    public static final String CATEGORY_LIST = BASE_PATH + "/category/list";
    public static final String CHECKOUT_CREATE = BASE_PATH + "/checkout/create";
    public static final String ORDER_LIST = BASE_PATH + "/order/list";
    public static final String ORDER_GET = BASE_PATH + "/order/get";
    public static final String ORDER_CANCEL = BASE_PATH + "/order/cancel";

    // Guest shopping paths
    public static final String GUEST_CHECKOUT = BASE_PATH + "/guest/checkout";
    public static final String GUEST_ORDER_TRACK = BASE_PATH + "/guest/order/track";

    // Seller storefront
    public static final String SELLER_SHOP = BASE_PATH + "/shop/get";
    public static final String SELLER_PRODUCTS = BASE_PATH + "/shop/products";

    // Payment
    public static final String PAYMENT_METHODS = BASE_PATH + "/payment/methods";
    public static final String PAYMENT_INTENT = BASE_PATH + "/payment/intent";

    // Loyalty
    public static final String LOYALTY_ACCOUNT = BASE_PATH + "/loyalty/account";
    public static final String LOYALTY_HUB = BASE_PATH + "/loyalty/hub";
    public static final String LOYALTY_CHECKIN = BASE_PATH + "/loyalty/checkin";
    public static final String LOYALTY_CHECKIN_CALENDAR = BASE_PATH + "/loyalty/checkin/calendar";
    public static final String LOYALTY_TRANSACTIONS = BASE_PATH + "/loyalty/transactions";
    public static final String LOYALTY_REWARDS = BASE_PATH + "/loyalty/rewards";
    public static final String LOYALTY_REDEEM = BASE_PATH + "/loyalty/rewards/redeem";
    public static final String LOYALTY_REDEMPTIONS = BASE_PATH + "/loyalty/redemptions";
    public static final String LOYALTY_ONBOARDING = BASE_PATH + "/loyalty/onboarding/tasks";
    public static final String WELCOME_SUMMARY = BASE_PATH + "/welcome/summary";
    public static final String INVITE_STATS = BASE_PATH + "/invite/stats";

    private BuyerApi() {
    }

    public record BuyerContextRequest(@NotBlank String buyerId) {
    }

    public record MergeGuestCartRequest(@NotBlank String guestBuyerId) {
    }

    public record DashboardResponse(ProfileApi.ProfileResponse profile,
                                    WalletApi.WalletAccountResponse wallet,
                                    List<PromotionApi.OfferResponse> promotions,
                                    List<MarketplaceApi.ProductResponse> marketplace,
                                    LoyaltyApi.AccountResponse loyalty) {
    }

    public record CheckoutRequest(@NotBlank String buyerId,
                                  String couponCode,
                                  String paymentMethod,
                                  Long pointsToUse) {
    }

    public record CheckoutResponse(List<OrderApi.OrderResponse> orders,
                                   java.math.BigDecimal totalPaid,
                                   String paymentMethod,
                                   String paymentIntentClientSecret,
                                   String paymentRedirectUrl) {
    }

    public record LoyaltyHubResponse(LoyaltyApi.AccountResponse account,
                                     List<LoyaltyApi.OnboardingTaskResponse> onboardingTasks,
                                     List<LoyaltyApi.RewardItemResponse> rewards,
                                     List<LoyaltyApi.TransactionResponse> recentTransactions,
                                     List<LoyaltyApi.RedemptionResponse> recentRedemptions) {
    }

    public record WelcomeSummaryResponse(
            @Schema(description = "当前积分余额", example = "100")
            long pointsBalance,
            @Schema(description = "欢迎券包")
            List<WelcomeCouponResponse> welcomeCoupons,
            @Schema(description = "新人任务列表")
            List<LoyaltyApi.OnboardingTaskResponse> onboardingTasks,
            @Schema(description = "当前用户专属邀请码", example = "INV-01HXABCD12")
            String inviteCode) {
    }

    public record WelcomeCouponResponse(
            @Schema(description = "优惠券编码", example = "WELCOME-5OFF-I")
            String code,
            @Schema(description = "优惠券标题", example = "$5 Off Welcome")
            String title,
            @Schema(description = "过期时间", example = "2026-04-06T12:00:00Z")
            Instant expiresAt) {
    }

    public record InviteRecordResponse(
            @Schema(description = "脱敏后的被邀请人昵称", example = "张***")
            String inviteeNickname,
            @Schema(description = "邀请记录状态", example = "REWARDED")
            String status,
            @Schema(description = "注册完成时间", example = "2026-03-23T12:00:00Z")
            Instant registeredAt) {
    }

    public record InviteStatsResponse(
            @Schema(description = "当前用户邀请码", example = "INV-01HXABCD12")
            String inviteCode,
            @Schema(description = "邀请链接", example = "https://shop.example.com/register?invite=INV-01HXABCD12")
            String inviteLink,
            @Schema(description = "总邀请人数", example = "5")
            int totalInvited,
            @Schema(description = "已发放奖励次数", example = "3")
            int totalRewarded,
            @Schema(description = "本月已奖励次数", example = "2")
            int monthlyRewardCount,
            @Schema(description = "本月奖励上限", example = "10")
            int monthlyRewardLimit,
            @Schema(description = "邀请记录详情")
            List<InviteRecordResponse> records) {
    }
}
