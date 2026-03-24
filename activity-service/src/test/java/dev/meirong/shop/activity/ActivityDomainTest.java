package dev.meirong.shop.activity;

import dev.meirong.shop.activity.domain.*;
import dev.meirong.shop.activity.engine.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActivityDomainTest {

    // --- ActivityGame state machine tests ---

    @Test
    void game_activate_from_draft() {
        ActivityGame game = new ActivityGame("g1", GameType.INSTANT_LOTTERY, "Test Lottery");
        assertEquals(GameStatus.DRAFT, game.getStatus());
        game.activate();
        assertEquals(GameStatus.ACTIVE, game.getStatus());
    }

    @Test
    void game_activate_from_active_throws() {
        ActivityGame game = new ActivityGame("g1", GameType.INSTANT_LOTTERY, "Test");
        game.activate();
        assertThrows(IllegalStateException.class, game::activate);
    }

    @Test
    void game_end_from_active() {
        ActivityGame game = new ActivityGame("g1", GameType.RED_ENVELOPE, "Red Packet");
        game.activate();
        game.end();
        assertEquals(GameStatus.ENDED, game.getStatus());
    }

    @Test
    void game_end_from_draft_throws() {
        ActivityGame game = new ActivityGame("g1", GameType.QUIZ, "Quiz");
        assertThrows(IllegalStateException.class, game::end);
    }

    @Test
    void game_cancel_from_draft() {
        ActivityGame game = new ActivityGame("g1", GameType.COLLECT_CARD, "Cards");
        game.cancel();
        assertEquals(GameStatus.CANCELLED, game.getStatus());
    }

    @Test
    void game_cancel_from_ended_throws() {
        ActivityGame game = new ActivityGame("g1", GameType.VIRTUAL_FARM, "Farm");
        game.activate();
        game.end();
        assertThrows(IllegalStateException.class, game::cancel);
    }

    @Test
    void game_isActive_respects_time_window() {
        ActivityGame game = new ActivityGame("g1", GameType.INSTANT_LOTTERY, "Lottery");
        game.activate();
        game.setStartAt(Instant.now().minusSeconds(3600));
        game.setEndAt(Instant.now().plusSeconds(3600));
        assertTrue(game.isActive());
    }

    @Test
    void game_isActive_false_when_expired() {
        ActivityGame game = new ActivityGame("g1", GameType.INSTANT_LOTTERY, "Lottery");
        game.activate();
        game.setStartAt(Instant.now().minusSeconds(7200));
        game.setEndAt(Instant.now().minusSeconds(3600));
        assertFalse(game.isActive());
    }

    // --- Prize stock tests ---

    @Test
    void prize_decrementStock_unlimited() {
        ActivityRewardPrize prize = new ActivityRewardPrize("p1", "g1", "Points", PrizeType.POINTS);
        // Default totalStock = -1 (unlimited)
        assertTrue(prize.hasStock());
        assertTrue(prize.decrementStock());
    }

    @Test
    void prize_decrementStock_limited() {
        ActivityRewardPrize prize = new ActivityRewardPrize("p1", "g1", "Coupon", PrizeType.COUPON);
        prize.setTotalStock(2);
        prize.setRemainingStock(2);
        assertTrue(prize.hasStock());
        assertTrue(prize.decrementStock());
        assertEquals(1, prize.getRemainingStock());
        assertTrue(prize.decrementStock());
        assertEquals(0, prize.getRemainingStock());
        assertFalse(prize.decrementStock());
        assertFalse(prize.hasStock());
    }

    // --- Participation tests ---

    @Test
    void participation_markWin() {
        ActivityParticipation p = new ActivityParticipation("id1", "g1", GameType.INSTANT_LOTTERY, "player1");
        p.markWin("prize1", "{\"name\":\"100 Points\"}");
        assertEquals("WIN", p.getResult());
        assertEquals("prize1", p.getPrizeId());
        assertEquals("PENDING", p.getRewardStatus());
    }

    @Test
    void participation_markMiss() {
        ActivityParticipation p = new ActivityParticipation("id2", "g1", GameType.INSTANT_LOTTERY, "player1");
        p.markMiss();
        assertEquals("MISS", p.getResult());
        assertEquals("SKIPPED", p.getRewardStatus());
    }

    @Test
    void participation_markDispatched() {
        ActivityParticipation p = new ActivityParticipation("id3", "g1", GameType.RED_ENVELOPE, "player1");
        p.markWin("prize1", "{}");
        p.markDispatched("ref-123");
        assertEquals("DISPATCHED", p.getRewardStatus());
        assertEquals("ref-123", p.getRewardRef());
    }

    @Test
    void virtualFarm_water_advancesStagesAndMaturity() {
        ActivityVirtualFarm farm = new ActivityVirtualFarm("farm-1", "g1", "player1", 2, 50);
        farm.water(50);
        assertEquals(2, farm.getStage());
        assertEquals(0, farm.getProgress());
        assertFalse(farm.isMatured());

        farm.water(50);
        assertEquals(2, farm.getStage());
        assertEquals(50, farm.getProgress());
        assertTrue(farm.isMatured());
    }

    @Test
    void virtualFarm_markHarvested_marksHarvested() {
        ActivityVirtualFarm farm = new ActivityVirtualFarm("farm-2", "g1", "player1", 1, 10);
        farm.water(10);
        farm.markHarvested();
        assertTrue(farm.isHarvested());
    }

    // --- ParticipateResult tests ---

    @Test
    void participateResult_miss() {
        ParticipateResult result = ParticipateResult.miss("No luck");
        assertFalse(result.win());
        assertNull(result.prizeId());
        assertEquals("No luck", result.message());
    }

    @Test
    void participateResult_win() {
        ParticipateResult result = ParticipateResult.win("p1", "100 Points", PrizeType.POINTS,
                BigDecimal.valueOf(100), "{\"target_index\":2}");
        assertTrue(result.win());
        assertEquals("p1", result.prizeId());
        assertEquals(PrizeType.POINTS, result.prizeType());
    }

    // --- GamePluginRegistry tests ---

    @Test
    void registry_requirePlugin_throws_for_unknown() {
        GamePluginRegistry registry = new GamePluginRegistry(List.of());
        assertThrows(IllegalArgumentException.class, () -> registry.requirePlugin(GameType.QUIZ));
    }
}
