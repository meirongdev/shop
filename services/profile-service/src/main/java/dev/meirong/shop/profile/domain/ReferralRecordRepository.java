package dev.meirong.shop.profile.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralRecordRepository extends JpaRepository<ReferralRecordEntity, String> {

    Optional<ReferralRecordEntity> findByInviteeId(String inviteeId);

    long countByReferrerId(String referrerId);

    long countByReferrerIdAndStatus(String referrerId, String status);

    long countByReferrerIdAndStatusAndRewardIssuedAtBetween(
            String referrerId, String status, Instant startInclusive, Instant endExclusive);

    List<ReferralRecordEntity> findTop20ByReferrerIdOrderByCreatedAtDesc(String referrerId);
}
