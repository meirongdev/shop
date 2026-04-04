package dev.meirong.shop.wallet.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletOutboxEventRepository extends JpaRepository<WalletOutboxEventEntity, String> {

    List<WalletOutboxEventEntity> findTop20ByPublishedFalseOrderByCreatedAtAsc();
}
