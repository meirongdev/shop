package dev.meirong.shop.wallet.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransactionEntity, String> {

    List<WalletTransactionEntity> findTop10ByPlayerIdOrderByCreatedAtDesc(String playerId);
}
