package dev.meirong.shop.sellerbff.client;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.wallet.WalletApi;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface WalletServiceClient {

    @PostExchange(WalletApi.GET)
    ApiResponse<WalletApi.WalletAccountResponse> getWallet(
            @RequestBody WalletApi.GetWalletRequest request);

    @PostExchange(WalletApi.WITHDRAW)
    ApiResponse<WalletApi.TransactionResponse> withdraw(
            @RequestBody WalletApi.WithdrawRequest request);
}
