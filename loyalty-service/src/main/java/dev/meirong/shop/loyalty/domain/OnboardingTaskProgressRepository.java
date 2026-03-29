package dev.meirong.shop.loyalty.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingTaskProgressRepository extends JpaRepository<OnboardingTaskProgressEntity, String> {

    List<OnboardingTaskProgressEntity> findByBuyerId(String buyerId);

    Optional<OnboardingTaskProgressEntity> findByBuyerIdAndTaskKey(String buyerId, String taskKey);
}
