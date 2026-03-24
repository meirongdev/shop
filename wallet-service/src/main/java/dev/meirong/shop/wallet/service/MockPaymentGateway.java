package dev.meirong.shop.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "shop.wallet.stripe-enabled", havingValue = "false", matchIfMissing = true)
public class MockPaymentGateway implements StripeGateway {

    @Override
    public PaymentReference createDeposit(String playerId, BigDecimal amount, String currency) {
        return new PaymentReference("MOCK-DEP-" + UUID.randomUUID(), "MOCK");
    }

    @Override
    public PaymentReference createWithdrawal(String playerId, BigDecimal amount, String currency) {
        return new PaymentReference("MOCK-WIT-" + UUID.randomUUID(), "MOCK");
    }
}
