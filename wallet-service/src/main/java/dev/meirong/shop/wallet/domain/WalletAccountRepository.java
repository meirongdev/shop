package dev.meirong.shop.wallet.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletAccountRepository extends JpaRepository<WalletAccountEntity, String> {
}
