package dev.meirong.shop.loyalty.service;

import dev.meirong.shop.loyalty.domain.LoyaltyAccountEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyRedemptionEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyRedemptionRepository;
import dev.meirong.shop.loyalty.domain.LoyaltyRewardItemEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyRewardItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RedemptionService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionService.class);

    private final LoyaltyRewardItemRepository rewardItemRepository;
    private final LoyaltyRedemptionRepository redemptionRepository;
    private final LoyaltyAccountService accountService;

    public RedemptionService(LoyaltyRewardItemRepository rewardItemRepository,
                             LoyaltyRedemptionRepository redemptionRepository,
                             LoyaltyAccountService accountService) {
        this.rewardItemRepository = rewardItemRepository;
        this.redemptionRepository = redemptionRepository;
        this.accountService = accountService;
    }

    public List<LoyaltyRewardItemEntity> listActiveRewards() {
        return rewardItemRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    @Transactional
    public LoyaltyRedemptionEntity redeem(String buyerId, String rewardItemId, int quantity) {
        LoyaltyRewardItemEntity item = rewardItemRepository.findById(rewardItemId)
                .orElseThrow(() -> new IllegalArgumentException("Reward item not found: " + rewardItemId));

        if (!item.isActive()) {
            throw new IllegalStateException("Reward item is not active");
        }

        long totalCost = item.getPointsRequired() * quantity;

        // Verify balance
        LoyaltyAccountEntity account = accountService.getOrCreateAccount(buyerId);
        if (account.getBalance() < totalCost) {
            throw new IllegalStateException("Insufficient points: need " + totalCost + ", have " + account.getBalance());
        }

        // Decrement stock
        if (!item.decrementStock(quantity)) {
            throw new IllegalStateException("Insufficient stock for reward: " + item.getName());
        }
        rewardItemRepository.save(item);

        // Deduct points
        String redemptionId = UUID.randomUUID().toString();
        accountService.deductPoints(buyerId, "REDEMPTION", totalCost,
                "redemption-" + redemptionId, "Redeemed " + item.getName() + " x" + quantity);

        // Create redemption record
        LoyaltyRedemptionEntity redemption = new LoyaltyRedemptionEntity(
                buyerId, rewardItemId, item.getName(), totalCost, quantity, item.getType());

        // Auto-complete for COUPON type
        if ("COUPON".equals(item.getType())) {
            String couponCode = "CP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            redemption.markCompleted(couponCode, null);
        }

        redemptionRepository.save(redemption);
        log.info("Player {} redeemed {} x{} for {} points", buyerId, item.getName(), quantity, totalCost);
        return redemption;
    }

    public Page<LoyaltyRedemptionEntity> getRedemptions(String buyerId, Pageable pageable) {
        return redemptionRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable);
    }
}
