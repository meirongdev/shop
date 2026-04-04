package dev.meirong.shop.marketplace.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceOutboxEventRepository extends JpaRepository<MarketplaceOutboxEventEntity, String> {
    List<MarketplaceOutboxEventEntity> findTop100ByPublishedFalseOrderByCreatedAtAsc();
}
