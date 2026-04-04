package dev.meirong.shop.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.api.WalletApi;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.WalletTransactionEventData;
import dev.meirong.shop.wallet.config.WalletProperties;
import dev.meirong.shop.wallet.domain.WalletAccountEntity;
import dev.meirong.shop.wallet.domain.WalletAccountRepository;
import dev.meirong.shop.wallet.domain.WalletIdempotencyKeyEntity;
import dev.meirong.shop.wallet.domain.WalletIdempotencyKeyRepository;
import dev.meirong.shop.wallet.domain.WalletOutboxEventEntity;
import dev.meirong.shop.wallet.domain.WalletOutboxEventRepository;
import dev.meirong.shop.wallet.domain.WalletTransactionEntity;
import dev.meirong.shop.wallet.domain.WalletTransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletApplicationService {

    private final WalletAccountRepository accountRepository;
    private final WalletTransactionRepository transactionRepository;
    private final WalletOutboxEventRepository outboxEventRepository;
    private final WalletIdempotencyKeyRepository idempotencyKeyRepository;
    private final StripeGateway stripeGateway;
    private final WalletProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public WalletApplicationService(WalletAccountRepository accountRepository,
                                    WalletTransactionRepository transactionRepository,
                                    WalletOutboxEventRepository outboxEventRepository,
                                    WalletIdempotencyKeyRepository idempotencyKeyRepository,
                                    StripeGateway stripeGateway,
                                    WalletProperties properties,
                                    ObjectMapper objectMapper,
                                    MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.stripeGateway = stripeGateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public WalletApi.WalletAccountResponse getWallet(WalletApi.GetWalletRequest request) {
        WalletAccountEntity account = accountRepository.findById(request.buyerId())
                .orElseGet(() -> accountRepository.save(new WalletAccountEntity(request.buyerId(), BigDecimal.ZERO)));
        return toWalletResponse(account);
    }

    @Transactional
    public WalletApi.TransactionResponse deposit(WalletApi.DepositRequest request) {
        return createDeposit(request, null);
    }

    @Transactional
    public WalletApi.TransactionResponse depositWithIdempotency(WalletApi.DepositRequest request, String idempotencyKey) {
        return createDeposit(request, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public WalletApi.TransactionResponse findByIdempotencyKey(String idempotencyKey) {
        return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .flatMap(key -> transactionRepository.findById(key.getTransactionId()))
                .map(this::toTransactionResponse)
                .orElseThrow(() -> new IllegalStateException("No transaction found for idempotency key: " + idempotencyKey));
    }

    private WalletApi.TransactionResponse createDeposit(WalletApi.DepositRequest request, String idempotencyKey) {
        WalletAccountEntity account = accountRepository.findById(request.buyerId())
                .orElseGet(() -> accountRepository.save(new WalletAccountEntity(request.buyerId(), BigDecimal.ZERO)));
        StripeGateway.PaymentReference paymentReference = stripeGateway.createDeposit(request.buyerId(), request.amount(), request.currency());
        account.credit(request.amount());
        accountRepository.save(account);
        WalletTransactionEntity transaction = transactionRepository.save(
                new WalletTransactionEntity(request.buyerId(), "DEPOSIT", request.amount(), request.currency(), "COMPLETED", paymentReference.providerReference())
        );
        if (idempotencyKey != null) {
            idempotencyKeyRepository.save(new WalletIdempotencyKeyEntity(idempotencyKey, transaction.getId()));
        }
        publishableEvent(transaction);
        Counter.builder("shop_wallet_deposit_total")
                .description("Total number of wallet deposits")
                .tag("currency", request.currency())
                .register(meterRegistry).increment();
        return toTransactionResponse(transaction);
    }

    @Transactional
    public WalletApi.TransactionResponse withdraw(WalletApi.WithdrawRequest request) {
        WalletAccountEntity account = accountRepository.findById(request.buyerId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Wallet account not found: " + request.buyerId()));
        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new BusinessException(CommonErrorCode.INSUFFICIENT_BALANCE, "Insufficient balance");
        }
        StripeGateway.PaymentReference paymentReference = stripeGateway.createWithdrawal(request.buyerId(), request.amount(), request.currency());
        account.debit(request.amount());
        accountRepository.save(account);
        WalletTransactionEntity transaction = transactionRepository.save(
                new WalletTransactionEntity(request.buyerId(), "WITHDRAW", request.amount(), request.currency(), "COMPLETED", paymentReference.providerReference())
        );
        publishableEvent(transaction);
        return toTransactionResponse(transaction);
    }

    @Transactional
    public WalletApi.TransactionResponse payForOrder(WalletApi.CreatePaymentRequest request) {
        WalletAccountEntity account = accountRepository.findById(request.buyerId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Wallet account not found: " + request.buyerId()));
        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new BusinessException(CommonErrorCode.INSUFFICIENT_BALANCE, "Insufficient balance for order payment");
        }
        Timer.Sample sample = Timer.start(meterRegistry);
        account.debit(request.amount());
        accountRepository.save(account);
        WalletTransactionEntity transaction = transactionRepository.save(
                new WalletTransactionEntity(request.buyerId(), "ORDER_PAYMENT", request.amount(),
                        request.currency(), "COMPLETED", "WALLET",
                        request.referenceId(), request.referenceType())
        );
        sample.stop(Timer.builder("shop_payment_duration_seconds")
                .description("Duration of order payment processing")
                .register(meterRegistry));
        Counter.builder("shop_payment_success_total")
                .description("Total successful order payments")
                .register(meterRegistry).increment();
        return toTransactionResponse(transaction);
    }

    @Transactional
    public WalletApi.TransactionResponse refundOrder(WalletApi.CreateRefundRequest request) {
        WalletAccountEntity account = accountRepository.findById(request.buyerId())
                .orElseGet(() -> accountRepository.save(new WalletAccountEntity(request.buyerId(), BigDecimal.ZERO)));
        account.credit(request.amount());
        accountRepository.save(account);
        WalletTransactionEntity transaction = transactionRepository.save(
                new WalletTransactionEntity(request.buyerId(), "ORDER_REFUND", request.amount(),
                        request.currency(), "COMPLETED", "WALLET",
                        request.referenceId(), request.referenceType())
        );
        return toTransactionResponse(transaction);
    }

    private WalletApi.WalletAccountResponse toWalletResponse(WalletAccountEntity account) {
        List<WalletApi.TransactionResponse> recentTransactions = transactionRepository
                .findTop10ByBuyerIdOrderByCreatedAtDesc(account.getBuyerId())
                .stream()
                .map(this::toTransactionResponse)
                .toList();
        return new WalletApi.WalletAccountResponse(account.getBuyerId(), account.getBalance(), account.getUpdatedAt(), recentTransactions);
    }

    private WalletApi.TransactionResponse toTransactionResponse(WalletTransactionEntity transaction) {
        return new WalletApi.TransactionResponse(
                transaction.getId(),
                transaction.getBuyerId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus(),
                transaction.getProviderReference(),
                transaction.getCreatedAt()
        );
    }

    private void publishableEvent(WalletTransactionEntity transaction) {
        BigDecimal balance = accountRepository.findById(transaction.getBuyerId())
                .map(WalletAccountEntity::getBalance).orElse(BigDecimal.ZERO);
        EventEnvelope<WalletTransactionEventData> event = new EventEnvelope<>(
                UUID.randomUUID().toString(),
                "wallet-service",
                "wallet.transaction.completed",
                Instant.now(),
                new WalletTransactionEventData(
                        transaction.getId(),
                        transaction.getBuyerId(),
                        null,
                        transaction.getType(),
                        transaction.getAmount(),
                        balance,
                        transaction.getCurrency(),
                        transaction.getStatus(),
                        transaction.getCreatedAt()
                )
        );
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventRepository.save(new WalletOutboxEventEntity(transaction.getId(), properties.walletTopic(), event.type(), payload));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Failed to serialize wallet event", exception);
        }
    }
}
