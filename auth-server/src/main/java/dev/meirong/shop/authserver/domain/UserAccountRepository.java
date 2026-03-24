package dev.meirong.shop.authserver.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, Long> {
    Optional<UserAccountEntity> findByUsername(String username);
    Optional<UserAccountEntity> findByPrincipalId(String principalId);
    Optional<UserAccountEntity> findByEmail(String email);
    Optional<UserAccountEntity> findByPhoneNumber(String phoneNumber);
}
