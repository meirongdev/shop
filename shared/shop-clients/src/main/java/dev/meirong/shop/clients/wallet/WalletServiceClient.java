package dev.meirong.shop.clients.wallet;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.wallet.WalletApi;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Shared {@code @HttpExchange} client for wallet-service.
 */
@HttpExchange
public interface WalletServiceClient {

    @PostExchange(WalletApi.GET)
    ApiResponse<WalletApi.WalletAccountResponse> getWallet(@RequestBody WalletApi.GetWalletRequest request);

    @PostExchange(WalletApi.DEPOSIT)
    ApiResponse<WalletApi.TransactionResponse> deposit(@RequestBody WalletApi.DepositRequest request);

    @PostExchange(WalletApi.WITHDRAW)
    ApiResponse<WalletApi.TransactionResponse> withdraw(@RequestBody WalletApi.WithdrawRequest request);

    @GetExchange(WalletApi.PAYMENT_METHODS)
    ApiResponse<List<WalletApi.PaymentMethodInfo>> listPaymentMethods();

    @PostExchange(WalletApi.PAYMENT_INTENT)
    ApiResponse<WalletApi.PaymentIntentResponse> createPaymentIntent(@RequestBody WalletApi.CreatePaymentIntentRequest request);

    @PostExchange(WalletApi.PAYMENT_CREATE)
    ApiResponse<WalletApi.TransactionResponse> createPayment(@RequestBody WalletApi.CreatePaymentRequest request);

    @PostExchange(WalletApi.PAYMENT_REFUND)
    ApiResponse<WalletApi.TransactionResponse> createRefund(@RequestBody WalletApi.CreateRefundRequest request);
}
