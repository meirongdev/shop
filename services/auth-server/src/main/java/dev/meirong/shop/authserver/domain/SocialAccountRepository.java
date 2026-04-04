package dev.meirong.shop.authserver.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccountEntity, Long> {
    Optional<SocialAccountEntity> findByProviderAndProviderUserId(String provider, String providerUserId);
    List<SocialAccountEntity> findByUserAccountId(Long userAccountId);
}
