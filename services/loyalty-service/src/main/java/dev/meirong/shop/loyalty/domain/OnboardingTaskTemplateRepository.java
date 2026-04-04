package dev.meirong.shop.loyalty.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingTaskTemplateRepository extends JpaRepository<OnboardingTaskTemplateEntity, String> {

    List<OnboardingTaskTemplateEntity> findByActiveTrueOrderBySortOrderAsc();
}
