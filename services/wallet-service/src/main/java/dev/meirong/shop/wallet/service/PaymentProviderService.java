package dev.meirong.shop.wallet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.api.WalletApi;
import dev.meirong.shop.wallet.config.WalletProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Routes payment operations to the appropriate provider (Stripe, PayPal, Wallet).
 * Stripe supports card, Apple Pay, and Google Pay via Payment Request API.
 */
@Service
public class PaymentProviderService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProviderService.class);

    private final StripeGateway stripeGateway;
    private final WalletProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PaymentProviderService(StripeGateway stripeGateway,
                                  WalletProperties properties,
                                  RestClient.Builder builder,
                                  ObjectMapper objectMapper) {
        this.stripeGateway = stripeGateway;
        this.properties = properties;
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a Stripe PaymentIntent for card/Apple Pay/Google Pay.
     * The client_secret is returned so the frontend can confirm using Stripe.js.
     */
    public WalletApi.PaymentIntentResponse createPaymentIntent(WalletApi.CreatePaymentIntentRequest request) {
        WalletApi.PaymentMethod method;
        try {
            method = WalletApi.PaymentMethod.valueOf(request.paymentMethod());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Unknown payment method: " + request.paymentMethod());
        }

        return switch (method) {
            case STRIPE_CARD, APPLE_PAY, GOOGLE_PAY -> createStripeIntent(request);
            case PAYPAL -> createPaypalOrder(request);
            case KLARNA -> createKlarnaSession(request);
            case WALLET -> throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Wallet payments are processed directly, no intent needed");
        };
    }

    /**
     * Lists all available payment methods based on configuration.
     */
    public List<WalletApi.PaymentMethodInfo> listPaymentMethods() {
        List<WalletApi.PaymentMethodInfo> methods = new ArrayList<>();
        methods.add(new WalletApi.PaymentMethodInfo("WALLET", "Wallet Balance", true, "INTERNAL"));

        boolean stripeReady = properties.stripeEnabled();
        methods.add(new WalletApi.PaymentMethodInfo("STRIPE_CARD", "Credit/Debit Card", stripeReady, "STRIPE"));
        methods.add(new WalletApi.PaymentMethodInfo("APPLE_PAY", "Apple Pay", stripeReady, "STRIPE"));
        methods.add(new WalletApi.PaymentMethodInfo("GOOGLE_PAY", "Google Pay", stripeReady, "STRIPE"));

        methods.add(new WalletApi.PaymentMethodInfo("PAYPAL", "PayPal", properties.paypalEnabled(), "PAYPAL"));
        methods.add(new WalletApi.PaymentMethodInfo("KLARNA", "Klarna Pay Later", properties.klarnaEnabled(), "KLARNA"));

        return methods;
    }

    private WalletApi.PaymentIntentResponse createStripeIntent(WalletApi.CreatePaymentIntentRequest request) {
        if (!properties.stripeEnabled()) {
            // Mock mode: return a simulated intent for development
            String mockId = "pi_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String mockSecret = mockId + "_secret_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            log.info("Created mock Stripe PaymentIntent: {} for {} {} ({})",
                    mockId, request.amount(), request.currency(), request.paymentMethod());
            return new WalletApi.PaymentIntentResponse(mockId, mockSecret, "STRIPE_MOCK", "requires_payment_method", null);
        }

        StripeGateway.PaymentReference ref = stripeGateway.createDeposit(
                request.buyerId(), request.amount(), request.currency());
        log.info("Created Stripe PaymentIntent: {} for {} {}", ref.providerReference(), request.amount(), request.currency());
        return new WalletApi.PaymentIntentResponse(ref.providerReference(), null, "STRIPE", "requires_confirmation", null);
    }

    private WalletApi.PaymentIntentResponse createPaypalOrder(WalletApi.CreatePaymentIntentRequest request) {
        if (!properties.paypalEnabled()) {
            String mockId = "PAYPAL-MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            log.info("Created mock PayPal order: {} for {} {}", mockId, request.amount(), request.currency());
            return new WalletApi.PaymentIntentResponse(
                    mockId,
                    null,
                    "PAYPAL_MOCK",
                    "CREATED",
                    "https://www.sandbox.paypal.com/checkoutnow?token=" + mockId);
        }
        try {
            String accessToken = fetchPaypalAccessToken();
            JsonNode response = restClient.post()
                    .uri(properties.paypalBaseUrl() + "/v2/checkout/orders")
                    .header("Authorization", "Bearer " + accessToken)
                    .body(java.util.Map.of(
                            "intent", "CAPTURE",
                            "purchase_units", List.of(java.util.Map.of(
                                    "reference_id", request.buyerId(),
                                    "amount", java.util.Map.of(
                                            "currency_code", request.currency().toUpperCase(),
                                            "value", request.amount().toPlainString())))))
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Empty PayPal order response");
            }
            String orderId = response.path("id").asText();
            String redirectUrl = null;
            for (JsonNode link : response.path("links")) {
                if ("approve".equalsIgnoreCase(link.path("rel").asText())) {
                    redirectUrl = link.path("href").asText();
                    break;
                }
            }
            return new WalletApi.PaymentIntentResponse(orderId, null, "PAYPAL", response.path("status").asText(), redirectUrl);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "PayPal order creation failed", exception);
        }
    }

    private WalletApi.PaymentIntentResponse createKlarnaSession(WalletApi.CreatePaymentIntentRequest request) {
        if (!properties.klarnaEnabled()) {
            String mockId = "KLARNA-MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            return new WalletApi.PaymentIntentResponse(
                    mockId,
                    mockId + "-client-token",
                    "KLARNA_MOCK",
                    "CREATED",
                    "https://js.playground.klarna.com/mock/" + mockId);
        }
        try {
            JsonNode response = restClient.post()
                    .uri(properties.klarnaBaseUrl() + "/payments/v1/sessions")
                    .headers(headers -> headers.setBasicAuth(properties.klarnaUsername(), properties.klarnaPassword()))
                    .body(java.util.Map.of(
                            "purchase_country", "US",
                            "purchase_currency", request.currency().toUpperCase(),
                            "locale", "en-US",
                            "order_amount", request.amount().multiply(new BigDecimal("100")).intValue(),
                            "order_tax_amount", 0,
                            "order_lines", List.of(java.util.Map.of(
                                    "type", "digital",
                                    "name", "Order payment",
                                    "quantity", 1,
                                    "unit_price", request.amount().multiply(new BigDecimal("100")).intValue(),
                                    "total_amount", request.amount().multiply(new BigDecimal("100")).intValue(),
                                    "total_tax_amount", 0))))
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Empty Klarna session response");
            }
            return new WalletApi.PaymentIntentResponse(
                    response.path("session_id").asText(),
                    response.path("client_token").asText(null),
                    "KLARNA",
                    "CREATED",
                    response.path("redirect_url").asText(null));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "Klarna session creation failed", exception);
        }
    }

    private String fetchPaypalAccessToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        String response = restClient.post()
                .uri(properties.paypalBaseUrl() + "/v1/oauth2/token")
                .headers(headers -> headers.setBasicAuth(properties.paypalClientId(), properties.paypalClientSecret()))
                .body(form)
                .retrieve()
                .body(String.class);
        try {
            return objectMapper.readTree(response).path("access_token").asText();
        } catch (Exception exception) {
            throw new BusinessException(CommonErrorCode.DOWNSTREAM_ERROR, "PayPal token exchange failed", exception);
        }
    }
}
