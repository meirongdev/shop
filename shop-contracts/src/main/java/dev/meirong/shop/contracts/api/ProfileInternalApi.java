package dev.meirong.shop.contracts.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class ProfileInternalApi {

    public static final String BASE_PATH = "/profile/internal";
    public static final String BUYER_REGISTER = BASE_PATH + "/buyer/register";
    public static final String INVITE_STATS = BASE_PATH + "/invite/stats";
    public static final String REFERRAL_FIRST_ORDER = BASE_PATH + "/referral/first-order";

    private ProfileInternalApi() {
    }

    public record RegisterBuyerRequest(
            @Schema(description = "玩家 ID", example = "player-01HXABCD1234")
            @NotBlank String playerId,
            @Schema(description = "平台用户名", example = "alice123")
            @NotBlank String username,
            @Schema(description = "展示名", example = "alice123")
            @NotBlank String displayName,
            @Schema(description = "联系邮箱", example = "alice@example.com")
            @NotBlank String email,
            @Schema(description = "注册时填写的邀请码，可为空", example = "INV-01HXABCD12")
            String inviteCode) {
    }

    public record InviteStatsRequest(
            @Schema(description = "邀请人玩家 ID", example = "player-01HXABCD1234")
            @NotBlank String playerId) {
    }

    public record InviteRecord(
            @Schema(description = "脱敏后的被邀请人昵称", example = "张***")
            String inviteeNickname,
            @Schema(description = "记录状态", example = "REGISTERED")
            String status,
            @Schema(description = "注册时间", example = "2026-03-23T12:00:00Z")
            Instant registeredAt) {
    }

    public record InviteStatsResponse(
            @Schema(description = "邀请码", example = "INV-01HXABCD12")
            String inviteCode,
            @Schema(description = "邀请链接", example = "https://shop.example.com/register?invite=INV-01HXABCD12")
            String inviteLink,
            @Schema(description = "总邀请人数", example = "5")
            int totalInvited,
            @Schema(description = "总奖励次数", example = "3")
            int totalRewarded,
            @Schema(description = "本月奖励次数", example = "2")
            int monthlyRewardCount,
            @Schema(description = "月度奖励上限", example = "10")
            int monthlyRewardLimit,
            @Schema(description = "邀请记录")
            List<InviteRecord> records) {
    }

    public record ReferralFirstOrderRequest(
            @Schema(description = "完成首单的被邀请人 ID", example = "player-01HXINVITEE")
            @NotBlank String inviteeId) {
    }

    public record ReferralRewardResult(
            @Schema(description = "本次是否需要发放邀请奖励", example = "true")
            boolean rewardIssued,
            @Schema(description = "邀请人玩家 ID，若无奖励则为空", example = "player-01HXREFERRER")
            String referrerId) {
    }
}
