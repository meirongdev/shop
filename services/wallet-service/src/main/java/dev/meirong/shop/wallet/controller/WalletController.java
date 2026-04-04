package dev.meirong.shop.wallet.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import dev.meirong.shop.contracts.api.WalletApi;
import dev.meirong.shop.wallet.service.PaymentProviderService;
import dev.meirong.shop.wallet.service.WalletApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(WalletApi.BASE_PATH)
public class WalletController {

    private final WalletApplicationService service;
    private final PaymentProviderService paymentProviderService;
    private final IdempotencyGuard idempotencyGuard;

    public WalletController(WalletApplicationService service,
                            PaymentProviderService paymentProviderService,
                            IdempotencyGuard idempotencyGuard) {
        this.service = service;
        this.paymentProviderService = paymentProviderService;
        this.idempotencyGuard = idempotencyGuard;
    }

    @PostMapping("/account/get")
    public ApiResponse<WalletApi.WalletAccountResponse> getWallet(@Valid @RequestBody WalletApi.GetWalletRequest request) {
        return ApiResponse.success(service.getWallet(request));
    }

    @PostMapping("/deposit/create")
    @Transactional
    public ApiResponse<WalletApi.TransactionResponse> deposit(
            @RequestHeader(value = WalletApi.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody WalletApi.DepositRequest request) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String normalizedKey = idempotencyKey.trim();
            return ApiResponse.success(idempotencyGuard.executeOnce(
                    normalizedKey,
                    () -> service.depositWithIdempotency(request, normalizedKey),
                    () -> service.findByIdempotencyKey(normalizedKey)
            ));
        }
        return ApiResponse.success(service.deposit(request));
    }

    @PostMapping("/withdraw/create")
    public ApiResponse<WalletApi.TransactionResponse> withdraw(@Valid @RequestBody WalletApi.WithdrawRequest request) {
        return ApiResponse.success(service.withdraw(request));
    }

    @PostMapping("/payment/create")
    public ApiResponse<WalletApi.TransactionResponse> payForOrder(@Valid @RequestBody WalletApi.CreatePaymentRequest request) {
        return ApiResponse.success(service.payForOrder(request));
    }

    @PostMapping("/payment/refund")
    public ApiResponse<WalletApi.TransactionResponse> refundOrder(@Valid @RequestBody WalletApi.CreateRefundRequest request) {
        return ApiResponse.success(service.refundOrder(request));
    }

    @PostMapping("/payment/intent")
    public ApiResponse<WalletApi.PaymentIntentResponse> createPaymentIntent(
            @Valid @RequestBody WalletApi.CreatePaymentIntentRequest request) {
        return ApiResponse.success(paymentProviderService.createPaymentIntent(request));
    }

    @GetMapping("/payment/methods")
    public ApiResponse<List<WalletApi.PaymentMethodInfo>> listPaymentMethods() {
        return ApiResponse.success(paymentProviderService.listPaymentMethods());
    }
}
