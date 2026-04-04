package dev.meirong.shop.activity.engine;

import dev.meirong.shop.activity.domain.GameType;
import dev.meirong.shop.activity.domain.PrizeType;

import java.math.BigDecimal;

public record ParticipateResult(
    boolean win,
    String prizeId,
    String prizeName,
    PrizeType prizeType,
    BigDecimal prizeValue,
    String animationHint,
    String message
) {
    public static ParticipateResult miss(String message) {
        return new ParticipateResult(false, null, null, null, null, null, message);
    }

    public static ParticipateResult win(String prizeId, String prizeName, PrizeType prizeType,
                                         BigDecimal prizeValue, String animationHint) {
        return new ParticipateResult(true, prizeId, prizeName, prizeType, prizeValue, animationHint,
                "Congratulations! You won: " + prizeName);
    }
}
