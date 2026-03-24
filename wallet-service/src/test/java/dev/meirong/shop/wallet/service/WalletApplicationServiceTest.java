package dev.meirong.shop.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.contracts.api.WalletApi;
import dev.meirong.shop.wallet.config.WalletProperties;
import dev.meirong.shop.wallet.domain.WalletAccountEntity;
import dev.meirong.shop.wallet.domain.WalletAccountRepository;
import dev.meirong.shop.wallet.domain.WalletIdempotencyKeyEntity;
import dev.meirong.shop.wallet.domain.WalletIdempotencyKeyRepository;
import dev.meirong.shop.wallet.domain.WalletOutboxEventEntity;
import dev.meirong.shop.wallet.domain.WalletOutboxEventRepository;
import dev.meirong.shop.wallet.domain.WalletTransactionEntity;
import dev.meirong.shop.wallet.domain.WalletTransactionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletApplicationServiceTest {

    @Mock
    private WalletAccountRepository accountRepository;
    @Mock
    private WalletTransactionRepository transactionRepository;
    @Mock
    private WalletOutboxEventRepository outboxEventRepository;
    @Mock
    private WalletIdempotencyKeyRepository idempotencyKeyRepository;
    @Mock
    private StripeGateway stripeGateway;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final WalletProperties properties = new WalletProperties(
            true,
            "sk_test",
            "pk_test",
            false,
            "",
            "",
            "https://api-m.sandbox.paypal.com",
            false,
            "",
            "",
            "https://api.playground.klarna.com",
            "USD",
            "wallet-events");

    private WalletApplicationService service;

    @BeforeEach
    void setUp() {
        service = new WalletApplicationService(
                accountRepository, transactionRepository, outboxEventRepository,
                idempotencyKeyRepository,
                stripeGateway, properties, objectMapper, new SimpleMeterRegistry()
        );
    }

    @Test
    void getWallet_existingAccount_returnsWalletWithTransactions() {
        String playerId = "player-1";
        WalletAccountEntity account = new WalletAccountEntity(playerId, new BigDecimal("100.00"));

        WalletTransactionEntity tx = new WalletTransactionEntity(
                playerId, "DEPOSIT", new BigDecimal("100.00"), "USD", "COMPLETED", "pi_abc123"
        );

        when(accountRepository.findById(playerId)).thenReturn(Optional.of(account));
        when(transactionRepository.findTop10ByPlayerIdOrderByCreatedAtDesc(playerId)).thenReturn(List.of(tx));

        WalletApi.WalletAccountResponse result = service.getWallet(new WalletApi.GetWalletRequest(playerId));

        assertThat(result.playerId()).isEqualTo(playerId);
        assertThat(result.balance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.recentTransactions()).hasSize(1);
        assertThat(result.recentTransactions().get(0).type()).isEqualTo("DEPOSIT");
    }

    @Test
    void deposit_creditsAccountAndSavesTransaction() {
        String playerId = "player-2";
        BigDecimal depositAmount = new BigDecimal("50.00");
        WalletAccountEntity account = new WalletAccountEntity(playerId, new BigDecimal("100.00"));

        when(accountRepository.findById(playerId)).thenReturn(Optional.of(account));
        when(stripeGateway.createDeposit(eq(playerId), eq(depositAmount), eq("USD")))
                .thenReturn(new StripeGateway.PaymentReference("pi_dep456", "stripe"));
        when(accountRepository.save(any(WalletAccountEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(WalletTransactionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any(WalletOutboxEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WalletApi.TransactionResponse result = service.deposit(
                new WalletApi.DepositRequest(playerId, depositAmount, "USD")
        );

        assertThat(result.playerId()).isEqualTo(playerId);
        assertThat(result.type()).isEqualTo("DEPOSIT");
        assertThat(result.amount()).isEqualByComparingTo(depositAmount);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.providerReference()).isEqualTo("pi_dep456");
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
        verify(accountRepository).save(account);
        verify(transactionRepository).save(any(WalletTransactionEntity.class));
        verify(outboxEventRepository).save(any(WalletOutboxEventEntity.class));
    }

    @Test
    void depositWithIdempotency_firstCall_savesKeyAndReturnsResult() {
        String playerId = "player-5";
        String idempotencyKey = "idem-key-001";
        BigDecimal depositAmount = new BigDecimal("10.00");
        WalletAccountEntity account = new WalletAccountEntity(playerId, new BigDecimal("100.00"));

        when(accountRepository.findById(playerId)).thenReturn(Optional.of(account));
        when(stripeGateway.createDeposit(eq(playerId), eq(depositAmount), eq("USD")))
                .thenReturn(new StripeGateway.PaymentReference("pi_dep789", "stripe"));
        when(accountRepository.save(any(WalletAccountEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(WalletTransactionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyKeyRepository.save(any(WalletIdempotencyKeyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any(WalletOutboxEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WalletApi.TransactionResponse result = service.depositWithIdempotency(
                new WalletApi.DepositRequest(playerId, depositAmount, "USD"),
                idempotencyKey
        );

        assertThat(result.playerId()).isEqualTo(playerId);
        assertThat(result.type()).isEqualTo("DEPOSIT");
        verify(idempotencyKeyRepository).save(any(WalletIdempotencyKeyEntity.class));
    }

    @Test
    void findByIdempotencyKey_returnsExistingTransaction() {
        WalletTransactionEntity transaction = new WalletTransactionEntity(
                "player-6", "DEPOSIT", new BigDecimal("20.00"), "USD", "COMPLETED", "pi_dep900"
        );
        when(idempotencyKeyRepository.findByIdempotencyKey("idem-key-002"))
                .thenReturn(Optional.of(new WalletIdempotencyKeyEntity("idem-key-002", transaction.getId())));
        when(transactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));

        WalletApi.TransactionResponse result = service.findByIdempotencyKey("idem-key-002");

        assertThat(result.transactionId()).isEqualTo(transaction.getId());
        assertThat(result.playerId()).isEqualTo("player-6");
    }

    @Test
    void withdraw_sufficientBalance_succeeds() {
        String playerId = "player-3";
        BigDecimal withdrawAmount = new BigDecimal("30.00");
        WalletAccountEntity account = new WalletAccountEntity(playerId, new BigDecimal("100.00"));

        when(accountRepository.findById(playerId)).thenReturn(Optional.of(account));
        when(stripeGateway.createWithdrawal(eq(playerId), eq(withdrawAmount), eq("USD")))
                .thenReturn(new StripeGateway.PaymentReference("pi_wd789", "stripe"));
        when(accountRepository.save(any(WalletAccountEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(WalletTransactionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxEventRepository.save(any(WalletOutboxEventEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WalletApi.TransactionResponse result = service.withdraw(
                new WalletApi.WithdrawRequest(playerId, withdrawAmount, "USD")
        );

        assertThat(result.playerId()).isEqualTo(playerId);
        assertThat(result.type()).isEqualTo("WITHDRAW");
        assertThat(result.amount()).isEqualByComparingTo(withdrawAmount);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    void withdraw_insufficientBalance_throwsException() {
        String playerId = "player-4";
        BigDecimal withdrawAmount = new BigDecimal("200.00");
        WalletAccountEntity account = new WalletAccountEntity(playerId, new BigDecimal("50.00"));

        when(accountRepository.findById(playerId)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.withdraw(
                new WalletApi.WithdrawRequest(playerId, withdrawAmount, "USD")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient balance");
    }
}
