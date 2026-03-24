package dev.meirong.shop.loyalty.service;

import dev.meirong.shop.loyalty.config.LoyaltyProperties;
import dev.meirong.shop.loyalty.domain.LoyaltyCheckinEntity;
import dev.meirong.shop.loyalty.domain.LoyaltyCheckinRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@EnableConfigurationProperties(LoyaltyProperties.class)
public class CheckinService {

    private static final Logger log = LoggerFactory.getLogger(CheckinService.class);
    private static final int STREAK_CYCLE = 7;
    private static final int[] STREAK_BONUS = {0, 0, 0, 5, 5, 10, 10};

    private final LoyaltyCheckinRepository checkinRepository;
    private final LoyaltyAccountService accountService;
    private final LoyaltyProperties properties;

    public CheckinService(LoyaltyCheckinRepository checkinRepository,
                          LoyaltyAccountService accountService,
                          LoyaltyProperties properties) {
        this.checkinRepository = checkinRepository;
        this.accountService = accountService;
        this.properties = properties;
    }

    @Transactional
    public LoyaltyCheckinEntity checkin(String playerId) {
        LocalDate today = LocalDate.now();

        // Already checked in today?
        Optional<LoyaltyCheckinEntity> existing = checkinRepository.findByPlayerIdAndCheckinDate(playerId, today);
        if (existing.isPresent()) {
            return existing.get();
        }

        int streakDay = calculateStreak(playerId, today);
        long points = calculatePoints(streakDay);

        LoyaltyCheckinEntity checkin = new LoyaltyCheckinEntity(
                playerId, today, streakDay, points, false, 0);
        checkinRepository.save(checkin);

        accountService.earnPoints(playerId, "CHECKIN", points,
                "checkin-" + playerId + "-" + today, "Daily check-in day " + streakDay);

        log.info("Player {} checked in: day={}, points={}", playerId, streakDay, points);
        return checkin;
    }

    @Transactional
    public LoyaltyCheckinEntity makeupCheckin(String playerId, LocalDate date) {
        if (!date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Makeup date must be in the past");
        }
        if (ChronoUnit.DAYS.between(date, LocalDate.now()) > 7) {
            throw new IllegalArgumentException("Makeup date must be within last 7 days");
        }
        if (checkinRepository.findByPlayerIdAndCheckinDate(playerId, date).isPresent()) {
            throw new IllegalStateException("Already checked in on " + date);
        }

        // Check monthly makeup limit
        YearMonth month = YearMonth.from(date);
        long makeupCount = checkinRepository.countByPlayerIdAndIsMakeupTrueAndCheckinDateBetween(
                playerId, month.atDay(1), month.atEndOfMonth());
        if (makeupCount >= properties.maxMakeupPerMonth()) {
            throw new IllegalStateException("Monthly makeup limit reached (" + properties.maxMakeupPerMonth() + ")");
        }

        int makeupCost = properties.makeupCost();
        // Deduct makeup cost first
        accountService.deductPoints(playerId, "MAKEUP_COST", makeupCost,
                "makeup-cost-" + playerId + "-" + date, "Makeup check-in cost for " + date);

        int streakDay = 1; // Makeup doesn't extend streaks
        long points = properties.checkinBasePoints();

        LoyaltyCheckinEntity checkin = new LoyaltyCheckinEntity(
                playerId, date, streakDay, points, true, makeupCost);
        checkinRepository.save(checkin);

        accountService.earnPoints(playerId, "CHECKIN", points,
                "checkin-" + playerId + "-" + date, "Makeup check-in for " + date);

        return checkin;
    }

    public List<LoyaltyCheckinEntity> getCalendar(String playerId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        return checkinRepository.findByPlayerIdAndCheckinDateBetweenOrderByCheckinDateAsc(
                playerId, ym.atDay(1), ym.atEndOfMonth());
    }

    private int calculateStreak(String playerId, LocalDate today) {
        Optional<LoyaltyCheckinEntity> yesterday = checkinRepository
                .findByPlayerIdAndCheckinDate(playerId, today.minusDays(1));
        if (yesterday.isPresent()) {
            int prevStreak = yesterday.get().getStreakDay();
            return (prevStreak % STREAK_CYCLE) + 1;
        }
        return 1;
    }

    private long calculatePoints(int streakDay) {
        long base = properties.checkinBasePoints();
        int bonus = STREAK_BONUS[Math.min(streakDay, STREAK_CYCLE) - 1];
        return base + bonus;
    }
}
