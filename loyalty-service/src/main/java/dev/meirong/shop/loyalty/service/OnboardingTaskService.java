package dev.meirong.shop.loyalty.service;

import dev.meirong.shop.loyalty.domain.OnboardingTaskProgressEntity;
import dev.meirong.shop.loyalty.domain.OnboardingTaskProgressRepository;
import dev.meirong.shop.loyalty.domain.OnboardingTaskTemplateEntity;
import dev.meirong.shop.loyalty.domain.OnboardingTaskTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class OnboardingTaskService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingTaskService.class);
    private static final int ONBOARDING_EXPIRE_DAYS = 30;

    private final OnboardingTaskTemplateRepository templateRepository;
    private final OnboardingTaskProgressRepository progressRepository;
    private final LoyaltyAccountService accountService;

    public OnboardingTaskService(OnboardingTaskTemplateRepository templateRepository,
                                 OnboardingTaskProgressRepository progressRepository,
                                 LoyaltyAccountService accountService) {
        this.templateRepository = templateRepository;
        this.progressRepository = progressRepository;
        this.accountService = accountService;
    }

    @Transactional
    public void initForNewUser(String playerId) {
        List<OnboardingTaskTemplateEntity> templates = templateRepository.findByActiveTrueOrderBySortOrderAsc();
        Instant expireAt = Instant.now().plus(ONBOARDING_EXPIRE_DAYS, ChronoUnit.DAYS);

        for (OnboardingTaskTemplateEntity template : templates) {
            Optional<OnboardingTaskProgressEntity> existing =
                    progressRepository.findByPlayerIdAndTaskKey(playerId, template.getTaskKey());
            if (existing.isEmpty()) {
                OnboardingTaskProgressEntity progress =
                        OnboardingTaskProgressEntity.init(playerId, template.getTaskKey(), expireAt);
                progressRepository.save(progress);
            }
        }
        log.info("Initialized {} onboarding tasks for player {}", templates.size(), playerId);
    }

    @Transactional
    public OnboardingTaskProgressEntity completeTask(String playerId, String taskKey) {
        OnboardingTaskProgressEntity progress = progressRepository
                .findByPlayerIdAndTaskKey(playerId, taskKey)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskKey));

        if (progress.isCompleted()) {
            return progress; // idempotent
        }
        if (progress.isExpired()) {
            throw new IllegalStateException("Task has expired");
        }

        OnboardingTaskTemplateEntity template = templateRepository.findAll().stream()
                .filter(t -> t.getTaskKey().equals(taskKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Task template not found: " + taskKey));

        long points = template.getPointsReward();
        progress.complete(points);
        progressRepository.save(progress);

        accountService.earnPoints(playerId, "ONBOARDING", points,
                "onboard-" + playerId + "-" + taskKey, "Completed: " + template.getTitle());

        log.info("Player {} completed onboarding task: {} (+{} pts)", playerId, taskKey, points);
        return progress;
    }

    public List<OnboardingTaskProgressEntity> getProgress(String playerId) {
        return progressRepository.findByPlayerId(playerId);
    }
}
