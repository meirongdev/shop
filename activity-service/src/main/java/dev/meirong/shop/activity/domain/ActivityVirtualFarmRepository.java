package dev.meirong.shop.activity.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityVirtualFarmRepository extends JpaRepository<ActivityVirtualFarm, String> {

    Optional<ActivityVirtualFarm> findByGameIdAndPlayerId(String gameId, String playerId);
}
