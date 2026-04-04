package dev.meirong.shop.contracts.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class WalletApi {

    public static final String BASE_PATH = "/wallet/v1";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String GET = BASE_PATH + "/account/get";
    public static final String DEPOSIT = BASE_PATH + "/deposit/create";
    public static final String WITHDRAW = BASE_PATH + "/withdraw/create";
    public static final String PAYMENT_CREATE = BASE_PATH + "/payment/create";
    public static final String PAYMENT_REFUND = BASE_PATH + "/payment/refund";
    public static final String PAYMENT_INTENT = BASE_PATH + "/payment/intent";
    public static final String PAYMENT_METHODS = BASE_PATH + "/payment/methods";

    private WalletApi() {
    }

    /** Supported payment methods */
    public enum PaymentMethod {
        WALLET,
        STRIPE_CARD,
        APPLE_PAY,
        GOOGLE_PAY,
        PAYPAL,
        KLARNA
    }

    public record GetWalletRequest(@NotBlank String buyerId) {
    }

    public record DepositRequest(@NotBlank String buyerId,
                                 @NotNull BigDecimal amount,
                                 @NotBlank String currency) {
    }

    public record WithdrawRequest(@NotBlank String buyerId,
                                  @NotNull BigDecimal amount,
                                  @NotBlank String currency) {
    }

    public record CreatePaymentRequest(@NotBlank String buyerId,
                                       @NotNull BigDecimal amount,
                                       @NotBlank String currency,
                                       @NotBlank String referenceId,
                                       @NotBlank String referenceType) {
    }

    public record CreateRefundRequest(@NotBlank String buyerId,
                                      @NotNull BigDecimal amount,
                                      @NotBlank String currency,
                                      @NotBlank String referenceId,
                                      @NotBlank String referenceType) {
    }

    /** Request to create a Stripe PaymentIntent for card/Apple Pay/Google Pay checkout */
    public record CreatePaymentIntentRequest(
            @Schema(description = "买家 ID", example = "buyer-01HXABCD1234")
            @NotBlank String buyerId,
            @Schema(description = "支付金额", example = "99.90")
            @NotNull BigDecimal amount,
            @Schema(description = "币种", example = "usd")
            @NotBlank String currency,
            @Schema(description = "支付方式", example = "PAYPAL")
            @NotBlank String paymentMethod) {
    }

    /** Response containing Stripe client secret for frontend confirmation */
    public record PaymentIntentResponse(
            @Schema(description = "供应商侧支付/会话 ID", example = "pi_1234567890")
            String intentId,
            @Schema(description = "客户端确认所需密钥，Stripe 类支付使用", example = "pi_123_secret_456")
            String clientSecret,
            @Schema(description = "支付供应商", example = "PAYPAL")
            String provider,
            @Schema(description = "当前支付状态", example = "CREATED")
            String status,
            @Schema(description = "需要跳转时的外部支付地址", example = "https://www.paypal.com/checkoutnow?token=EC-123")
            String redirectUrl) {
    }

    /** Available payment method info */
    public record PaymentMethodInfo(String method,
                                    String displayName,
                                    boolean enabled,
                                    String provider) {
    }

    public record TransactionResponse(String transactionId,
                                      String buyerId,
                                      String type,
                                      BigDecimal amount,
                                      String currency,
                                      String status,
                                      String providerReference,
                                      Instant createdAt) {
    }

    public record WalletAccountResponse(String buyerId,
                                        BigDecimal balance,
                                        Instant updatedAt,
                                        List<TransactionResponse> recentTransactions) {
    }
}
