package dev.meirong.shop.profile.service;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.api.ProfileInternalApi;
import dev.meirong.shop.profile.domain.BuyerProfileEntity;
import dev.meirong.shop.profile.domain.BuyerProfileRepository;
import dev.meirong.shop.profile.domain.ReferralRecordEntity;
import dev.meirong.shop.profile.domain.ReferralRecordRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileReferralService {

    private static final int MONTHLY_REWARD_LIMIT = 10;

    private final BuyerProfileRepository buyerProfileRepository;
    private final ReferralRecordRepository referralRecordRepository;

    public ProfileReferralService(BuyerProfileRepository buyerProfileRepository,
                                  ReferralRecordRepository referralRecordRepository) {
        this.buyerProfileRepository = buyerProfileRepository;
        this.referralRecordRepository = referralRecordRepository;
    }

    @Transactional
    public void registerBuyer(ProfileInternalApi.RegisterBuyerRequest request) {
        if (buyerProfileRepository.existsById(request.playerId())) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Buyer profile already exists: " + request.playerId());
        }

        BuyerProfileEntity referrer = request.inviteCode() == null || request.inviteCode().isBlank()
                ? null
                : buyerProfileRepository.findByInviteCodeAndInviteCodeExpireAtAfter(request.inviteCode(), Instant.now())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Invalid invite code"));

        BuyerProfileEntity entity = BuyerProfileEntity.register(
                request.playerId(),
                request.username(),
                request.displayName(),
                request.email(),
                "SILVER",
                generateInviteCode(),
                Instant.now().plusSeconds(30L * 24 * 60 * 60),
                referrer != null ? referrer.getPlayerId() : null);
        buyerProfileRepository.save(entity);

        if (referrer != null) {
            referralRecordRepository.save(ReferralRecordEntity.registered(
                    UUID.randomUUID().toString(),
                    referrer.getInviteCode(),
                    referrer.getPlayerId(),
                    request.playerId(),
                    request.username()));
        }
    }

    @Transactional(readOnly = true)
    public ProfileInternalApi.InviteStatsResponse getInviteStats(String playerId) {
        BuyerProfileEntity profile = buyerProfileRepository.findById(playerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "Profile not found: " + playerId));
        int totalInvited = Math.toIntExact(referralRecordRepository.countByReferrerId(playerId));
        int totalRewarded = Math.toIntExact(referralRecordRepository.countByReferrerIdAndStatus(playerId, "REWARDED"));
        ZonedDateTime monthStart = ZonedDateTime.now(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime nextMonthStart = monthStart.plusMonths(1);
        int monthlyRewardCount = Math.toIntExact(referralRecordRepository.countByReferrerIdAndStatusAndRewardIssuedAtBetween(
                playerId, "REWARDED", monthStart.toInstant(), nextMonthStart.toInstant()));

        return new ProfileInternalApi.InviteStatsResponse(
                profile.getInviteCode(),
                "http://localhost:8080/buyer/register?invite=" + profile.getInviteCode(),
                totalInvited,
                totalRewarded,
                monthlyRewardCount,
                MONTHLY_REWARD_LIMIT,
                referralRecordRepository.findTop20ByReferrerIdOrderByCreatedAtDesc(playerId)
                        .stream()
                        .map(record -> new ProfileInternalApi.InviteRecord(
                                maskUsername(record.getInviteeUsername()),
                                record.getStatus(),
                                record.getRegisteredAt()))
                        .toList());
    }

    @Transactional
    public ProfileInternalApi.ReferralRewardResult markReferralFirstOrder(String inviteeId) {
        ReferralRecordEntity record = referralRecordRepository.findByInviteeId(inviteeId).orElse(null);
        if (record == null || !"REGISTERED".equals(record.getStatus())) {
            return new ProfileInternalApi.ReferralRewardResult(false, null);
        }
        ZonedDateTime monthStart = ZonedDateTime.now(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC);
        ZonedDateTime nextMonthStart = monthStart.plusMonths(1);
        long monthlyRewardCount = referralRecordRepository.countByReferrerIdAndStatusAndRewardIssuedAtBetween(
                record.getReferrerId(), "REWARDED", monthStart.toInstant(), nextMonthStart.toInstant());
        if (monthlyRewardCount >= MONTHLY_REWARD_LIMIT) {
            return new ProfileInternalApi.ReferralRewardResult(false, null);
        }
        record.markRewarded();
        return new ProfileInternalApi.ReferralRewardResult(true, record.getReferrerId());
    }

    private String generateInviteCode() {
        return "INV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private String maskUsername(String username) {
        if (username == null || username.isBlank()) {
            return "***";
        }
        return username.charAt(0) + "***";
    }
}
