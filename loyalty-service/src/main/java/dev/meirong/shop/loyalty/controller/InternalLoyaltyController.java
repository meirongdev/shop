package dev.meirong.shop.loyalty.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.api.LoyaltyApi;
import dev.meirong.shop.loyalty.domain.LoyaltyAccountEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyTransactionEntity;
import dev.meirong.shop.loyalty.service.LoyaltyAccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class InternalLoyaltyController {

    private final LoyaltyAccountService accountService;

    public InternalLoyaltyController(LoyaltyAccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping(LoyaltyApi.INTERNAL_EARN)
    public ApiResponse<LoyaltyApi.TransactionResponse> earnPoints(
            @Valid @RequestBody LoyaltyApi.EarnPointsRequest request) {
        LoyaltyTransactionEntity txn = accountService.earnPoints(
                request.playerId(), request.source(), request.points(),
                request.referenceId(), request.remark());
        return ApiResponse.success(toResponse(txn));
    }

    @PostMapping(LoyaltyApi.INTERNAL_DEDUCT)
    public ApiResponse<LoyaltyApi.TransactionResponse> deductPoints(
            @Valid @RequestBody LoyaltyApi.DeductPointsRequest request) {
        LoyaltyTransactionEntity txn = accountService.deductPoints(
                request.playerId(), request.source(), request.points(),
                request.referenceId(), request.remark());
        return ApiResponse.success(toResponse(txn));
    }

    @GetMapping(LoyaltyApi.INTERNAL_BALANCE + "/{playerId}")
    public ApiResponse<LoyaltyApi.AccountResponse> getBalance(@PathVariable String playerId) {
        LoyaltyAccountEntity account = accountService.getOrCreateAccount(playerId);
        return ApiResponse.success(new LoyaltyApi.AccountResponse(
                account.getPlayerId(), account.getTotalPoints(), account.getUsedPoints(),
                account.getBalance(), account.getTier(), account.getTierPoints()));
    }

    private LoyaltyApi.TransactionResponse toResponse(LoyaltyTransactionEntity e) {
        return new LoyaltyApi.TransactionResponse(
                e.getId(), e.getType(), e.getSource(), e.getAmount(),
                e.getBalanceAfter(), e.getReferenceId(), e.getRemark(), e.getCreatedAt());
    }
}
