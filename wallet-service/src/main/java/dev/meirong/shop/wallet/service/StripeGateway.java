package dev.meirong.shop.wallet.service;

import java.math.BigDecimal;

public interface StripeGateway {

    PaymentReference createDeposit(String playerId, BigDecimal amount, String currency);

    PaymentReference createWithdrawal(String playerId, BigDecimal amount, String currency);

    record PaymentReference(String providerReference, String provider) {
    }
}
