package dev.meirong.shop.profile.domain;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuyerProfileRepository extends JpaRepository<BuyerProfileEntity, String> {

    Optional<BuyerProfileEntity> findByInviteCode(String inviteCode);

    Optional<BuyerProfileEntity> findByInviteCodeAndInviteCodeExpireAtAfter(String inviteCode, Instant now);
}
