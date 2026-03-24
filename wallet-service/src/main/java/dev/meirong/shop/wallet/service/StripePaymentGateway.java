package dev.meirong.shop.wallet.service;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.wallet.config.WalletProperties;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Payout;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PayoutCreateParams;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "shop.wallet.stripe-enabled", havingValue = "true")
public class StripePaymentGateway implements StripeGateway {

    private final WalletProperties properties;

    public StripePaymentGateway(WalletProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentReference createDeposit(String buyerId, BigDecimal amount, String currency) {
        requireStripe();
        try {
            Stripe.apiKey = properties.stripeSecretKey();
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(toMinorUnits(amount))
                    .setCurrency(currency.toLowerCase())
                    .putMetadata("buyerId", buyerId)
                    .build();
            PaymentIntent intent = PaymentIntent.create(params);
            return new PaymentReference(intent.getId(), "STRIPE");
        } catch (StripeException exception) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Stripe deposit request failed", exception);
        }
    }

    @Override
    public PaymentReference createWithdrawal(String buyerId, BigDecimal amount, String currency) {
        requireStripe();
        try {
            Stripe.apiKey = properties.stripeSecretKey();
            PayoutCreateParams params = PayoutCreateParams.builder()
                    .setAmount(toMinorUnits(amount))
                    .setCurrency(currency.toLowerCase())
                    .putMetadata("buyerId", buyerId)
                    .build();
            Payout payout = Payout.create(params);
            return new PaymentReference(payout.getId(), "STRIPE");
        } catch (StripeException exception) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Stripe withdraw request failed", exception);
        }
    }

    private void requireStripe() {
        if (!properties.stripeEnabled() || properties.stripeSecretKey() == null || properties.stripeSecretKey().isBlank()) {
            throw new BusinessException(CommonErrorCode.PAYMENT_PROVIDER_DISABLED, "Stripe integration is not configured");
        }
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }
}
