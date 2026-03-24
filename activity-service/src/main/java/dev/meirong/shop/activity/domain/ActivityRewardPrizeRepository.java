package dev.meirong.shop.activity.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityRewardPrizeRepository extends JpaRepository<ActivityRewardPrize, String> {

    List<ActivityRewardPrize> findByGameIdOrderByDisplayOrderAsc(String gameId);
}
