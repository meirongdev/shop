package dev.meirong.shop.loyalty.service;

import dev.meirong.shop.loyalty.domain.LoyaltyAccountEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyAccountRepository;
import dev.meirong.shop.loyalty.domain.LoyaltyTransactionEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyTransactionRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PointsExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PointsExpiryScheduler.class);

    private final LoyaltyTransactionRepository transactionRepository;
    private final LoyaltyAccountRepository accountRepository;

    public PointsExpiryScheduler(LoyaltyTransactionRepository transactionRepository,
                                 LoyaltyAccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void expirePoints() {
        LocalDate today = LocalDate.now();
        List<String> playerIds = transactionRepository.findPlayerIdsWithExpirablePoints(today);
        if (playerIds.isEmpty()) {
            return;
        }
        log.info("Points expiry: processing {} players", playerIds.size());
        int totalExpired = 0;
        for (String buyerId : playerIds) {
            totalExpired += expireForPlayer(buyerId, today);
        }
        log.info("Points expiry completed: {} transactions expired", totalExpired);
    }

    @Transactional
    public int expireForPlayer(String buyerId, LocalDate today) {
        List<LoyaltyTransactionEntity> expirable = transactionRepository
                .findByPlayerIdAndTypeAndExpiredFalseAndExpireAtLessThanEqual(buyerId, "EARN", today);
        if (expirable.isEmpty()) {
            return 0;
        }

        long totalExpiring = expirable.stream().mapToLong(LoyaltyTransactionEntity::getAmount).sum();

        LoyaltyAccountEntity account = accountRepository.findById(buyerId).orElse(null);
        if (account == null) {
            return 0;
        }

        // Only expire up to current balance
        long actualExpire = Math.min(totalExpiring, account.getBalance());
        if (actualExpire <= 0) {
            // Mark as expired anyway so they won't be re-processed
            expirable.forEach(LoyaltyTransactionEntity::markExpired);
            transactionRepository.saveAll(expirable);
            return expirable.size();
        }

        account.expirePoints(actualExpire);
        accountRepository.save(account);

        // Record expiry transaction
        LoyaltyTransactionEntity expiryTxn = new LoyaltyTransactionEntity(
                buyerId, "EXPIRE", "SYSTEM", -actualExpire, account.getBalance(),
                "expiry-" + buyerId + "-" + today, "Points expired on " + today);
        transactionRepository.save(expiryTxn);

        expirable.forEach(LoyaltyTransactionEntity::markExpired);
        transactionRepository.saveAll(expirable);

        log.info("Player {} expired {} points ({} transactions)", buyerId, actualExpire, expirable.size());
        return expirable.size();
    }
}
