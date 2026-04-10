package dev.meirong.shop.contracts.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AuthApi {

    public static final String BASE_PATH = "/auth/v1";
    public static final String LOGIN = BASE_PATH + "/token/login";
    public static final String GUEST = BASE_PATH + "/token/guest";
    public static final String OAUTH2_GOOGLE = BASE_PATH + "/token/oauth2/google";
    public static final String OAUTH2_APPLE = BASE_PATH + "/token/oauth2/apple";
    public static final String BUYER_REGISTER = BASE_PATH + "/buyer/register";
    public static final String OTP_SEND = BASE_PATH + "/otp/send";
    public static final String OTP_VERIFY = BASE_PATH + "/otp/verify";
    public static final String SOCIAL_BIND = BASE_PATH + "/social/bind";
    public static final String SOCIAL_LIST = BASE_PATH + "/social/list";
    public static final String ME = BASE_PATH + "/user/me";

    private AuthApi() {
    }

    public record LoginRequest(
            @Schema(description = "登录用户名", example = "buyer.demo")
            @NotBlank String username,
            @Schema(description = "登录密码", example = "password")
            @NotBlank String password,
            @Schema(description = "门户类型", example = "buyer")
            @NotBlank String portal) {
    }

    public record GuestTokenRequest(
            @Schema(description = "访客会话所属门户", example = "buyer")
            @NotBlank String portal) {
    }

    public record OAuth2GoogleRequest(
            @Schema(description = "Google ID Token", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6Ij...")
            @NotBlank String idToken,
            @Schema(description = "门户类型", example = "buyer")
            @NotBlank String portal) {
    }

    public record OAuth2AppleRequest(
            @Schema(description = "Apple ID Token", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6IkFCQ0QifQ...")
            @NotBlank String idToken,
            @Schema(description = "与 Apple token 绑定的 nonce", example = "8c1e2e19b65d41f5b727d73316c8b8bf")
            @NotBlank String nonce,
            @Schema(description = "门户类型", example = "buyer")
            @NotBlank String portal) {
    }

    public record BuyerRegisterRequest(
            @Schema(description = "买家用户名", example = "alice123")
            @NotBlank
            @Size(min = 3, max = 64) String username,
            @Schema(description = "买家邮箱", example = "alice@example.com")
            @Email
            @NotBlank String email,
            @Schema(description = "登录密码，至少 8 位且包含大小写字母和数字", example = "Passw0rd!")
            @NotBlank
            @Size(min = 8, max = 128)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$")
            String password,
            @Schema(description = "邀请好友邀请码，可为空", example = "INV-01HXABCD12")
            String inviteCode) {
    }

    public record OtpSendRequest(
            @Schema(description = "E.164 格式手机号", example = "+8613800138000")
            @NotBlank String phoneNumber,
            @Schema(description = "语言区域", example = "zh-CN")
            @NotBlank String locale) {
    }

    public record OtpSendResponse(
            @Schema(description = "验证码有效期（秒）", example = "300")
            int expiresIn,
            @Schema(description = "发送冷却时间（秒）", example = "60")
            int cooldownRemaining) {
    }

    public record OtpVerifyRequest(
            @Schema(description = "E.164 格式手机号", example = "+8613800138000")
            @NotBlank String phoneNumber,
            @Schema(description = "6 位短信验证码", example = "123456")
            @NotBlank
            @Pattern(regexp = "^\\d{6}$")
            String otp,
            @Schema(description = "门户类型", example = "buyer")
            @NotBlank String portal) {
    }

    public record SocialBindRequest(
            @Schema(description = "社交账号提供方", example = "google")
            @NotBlank String provider,
            @Schema(description = "提供方返回的 ID Token", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6Ij...")
            @NotBlank String idToken) {
    }

    public record SocialAccountInfo(
            @Schema(description = "提供方", example = "google")
            String provider,
            @Schema(description = "提供方侧唯一用户标识", example = "google-sub-123")
            String providerUserId,
            @Schema(description = "社交账号邮箱", example = "alice@example.com")
            String email,
            @Schema(description = "社交账号展示名", example = "Alice")
            String name) {
    }

    public record TokenResponse(
            @Schema(description = "JWT 访问令牌", example = "eyJhbGciOiJIUzI1NiJ9...")
            String accessToken,
            @Schema(description = "令牌类型", example = "Bearer")
            String tokenType,
            @Schema(description = "令牌过期时间", example = "2026-03-23T20:00:00Z")
            Instant expiresAt,
            @Schema(description = "平台用户名", example = "alice123")
            String username,
            @Schema(description = "展示名", example = "Alice")
            String displayName,
            @Schema(description = "平台主体 ID", example = "buyer-01HXABCD1234")
            String principalId,
            @Schema(description = "角色列表", example = "[\"ROLE_BUYER\"]")
            List<String> roles,
            @Schema(description = "门户类型", example = "buyer")
            String portal,
            @Schema(description = "是否为首次登录/新注册用户", example = "false")
            boolean newUser) {
    }

    public record MeResponse(
            @Schema(description = "用户名", example = "buyer.demo")
            String username,
            @Schema(description = "展示名", example = "Buyer Demo")
            String displayName,
            @Schema(description = "主体 ID", example = "buyer-1001")
            String principalId,
            @Schema(description = "角色列表", example = "[\"ROLE_BUYER\"]")
            List<String> roles,
            @Schema(description = "门户类型", example = "buyer")
            String portal) {
    }
}
