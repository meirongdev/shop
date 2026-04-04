package dev.meirong.shop.loyalty.service;

import dev.meirong.shop.loyalty.domain.LoyaltyAccountEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyAccountRepository;
import dev.meirong.shop.loyalty.domain.LoyaltyEarnRuleEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyEarnRuleRepository;
import dev.meirong.shop.loyalty.domain.LoyaltyTransactionEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyTransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoyaltyAccountService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyAccountService.class);

    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final LoyaltyEarnRuleRepository earnRuleRepository;
    private final MeterRegistry meterRegistry;

    public LoyaltyAccountService(LoyaltyAccountRepository accountRepository,
                                 LoyaltyTransactionRepository transactionRepository,
                                 LoyaltyEarnRuleRepository earnRuleRepository,
                                 MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.earnRuleRepository = earnRuleRepository;
        this.meterRegistry = meterRegistry;
    }

    public LoyaltyAccountEntity getOrCreateAccount(String buyerId) {
        return accountRepository.findById(buyerId)
                .orElseGet(() -> accountRepository.save(new LoyaltyAccountEntity(buyerId)));
    }

    @Transactional
    public LoyaltyTransactionEntity earnPoints(String buyerId, String source,
                                               long points, String referenceId, String remark) {
        if (points <= 0) throw new IllegalArgumentException("Points must be positive");

        // Idempotency: skip if transaction with same referenceId already exists
        if (referenceId != null && !transactionRepository.findByReferenceId(referenceId).isEmpty()) {
            log.info("Duplicate earn request for referenceId={}, skipping", referenceId);
            return transactionRepository.findByReferenceId(referenceId).getFirst();
        }

        LoyaltyAccountEntity account = getOrCreateAccount(buyerId);
        account.earnPoints(points);
        accountRepository.save(account);

        LoyaltyTransactionEntity txn = new LoyaltyTransactionEntity(
                buyerId, "EARN", source, points, account.getBalance(), referenceId, remark);
        transactionRepository.save(txn);
        Counter.builder("shop_loyalty_points_earned_total")
                .description("Total loyalty points earned")
                .tag("source", source)
                .register(meterRegistry).increment(points);
        return txn;
    }

    @Transactional
    public LoyaltyTransactionEntity deductPoints(String buyerId, String source,
                                                 long points, String referenceId, String remark) {
        if (points <= 0) throw new IllegalArgumentException("Points must be positive");

        LoyaltyAccountEntity account = getOrCreateAccount(buyerId);
        account.deductPoints(points);
        accountRepository.save(account);

        LoyaltyTransactionEntity txn = new LoyaltyTransactionEntity(
                buyerId, "DEDUCT", source, -points, account.getBalance(), referenceId, remark);
        transactionRepository.save(txn);
        Counter.builder("shop_loyalty_points_redeemed_total")
                .description("Total loyalty points redeemed/deducted")
                .tag("source", source)
                .register(meterRegistry).increment(points);
        return txn;
    }

    @Transactional
    public LoyaltyTransactionEntity earnByRule(String buyerId, String ruleSource,
                                               double amount, String referenceId, String remark) {
        LoyaltyEarnRuleEntity rule = earnRuleRepository.findBySourceAndActiveTrue(ruleSource)
                .orElseThrow(() -> new IllegalStateException("No active earn rule for source: " + ruleSource));
        long points = rule.calculate(amount);
        if (points <= 0) return null;
        return earnPoints(buyerId, ruleSource, points, referenceId, remark);
    }

    public Page<LoyaltyTransactionEntity> getTransactions(String buyerId, Pageable pageable) {
        return transactionRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable);
    }
}
