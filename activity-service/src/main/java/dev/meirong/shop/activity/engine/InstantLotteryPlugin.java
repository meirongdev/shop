package dev.meirong.shop.activity.engine;

import dev.meirong.shop.activity.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

/**
 * Handles INSTANT_LOTTERY games (golden egg, lucky wheel, scratch card, nine-grid).
 * Uses weighted random selection based on prize probabilities.
 */
@Component
public class InstantLotteryPlugin implements GamePlugin {

    private static final Logger log = LoggerFactory.getLogger(InstantLotteryPlugin.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ActivityRewardPrizeRepository prizeRepository;

    public InstantLotteryPlugin(ActivityRewardPrizeRepository prizeRepository) {
        this.prizeRepository = prizeRepository;
    }

    @Override
    public GameType supportedType() {
        return GameType.INSTANT_LOTTERY;
    }

    @Override
    public ParticipateResult participate(ParticipateContext ctx) {
        List<ActivityRewardPrize> prizes = prizeRepository.findByGameIdOrderByDisplayOrderAsc(ctx.gameId());

        if (prizes.isEmpty()) {
            return ParticipateResult.miss("No prizes available");
        }

        // Weighted random draw
        double roll = RANDOM.nextDouble();
        double cumulative = 0.0;

        for (ActivityRewardPrize prize : prizes) {
            if (prize.getType() == PrizeType.NOTHING) continue;
            if (!prize.hasStock()) continue;

            cumulative += prize.getProbability().doubleValue();
            if (roll < cumulative) {
                // Try to claim this prize (decrement stock)
                if (prize.decrementStock()) {
                    prizeRepository.save(prize);
                    String animationHint = buildAnimationHint(prizes, prize);
                    log.info("Lottery win: game={}, prize={}, player={}", ctx.gameId(), prize.getName(), ctx.buyerId());
                    return ParticipateResult.win(
                            prize.getId(), prize.getName(), prize.getType(),
                            prize.getValue(), animationHint);
                }
                // Stock exhausted, treat as miss
                break;
            }
        }

        // Check for explicit NOTHING prize (guaranteed miss, with animation)
        Optional<ActivityRewardPrize> nothingPrize = prizes.stream()
                .filter(p -> p.getType() == PrizeType.NOTHING)
                .findFirst();
        String hint = nothingPrize.map(p -> buildAnimationHint(prizes, p)).orElse(null);
        return new ParticipateResult(false, null, null, null, null, hint, "Better luck next time!");
    }

    private String buildAnimationHint(List<ActivityRewardPrize> prizes, ActivityRewardPrize selected) {
        int targetIndex = prizes.indexOf(selected);
        return "{\"target_index\":%d,\"total_slots\":%d}".formatted(targetIndex, prizes.size());
    }
}
