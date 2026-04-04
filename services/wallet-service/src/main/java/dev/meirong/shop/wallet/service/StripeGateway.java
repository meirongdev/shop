package dev.meirong.shop.wallet.service;

import java.math.BigDecimal;

public interface StripeGateway {

    PaymentReference createDeposit(String buyerId, BigDecimal amount, String currency);

    PaymentReference createWithdrawal(String buyerId, BigDecimal amount, String currency);

    record PaymentReference(String providerReference, String provider) {
    }
}
