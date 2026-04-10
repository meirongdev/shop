package dev.meirong.shop.contracts.activity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public interface ActivityApi {

    String BASE = "/activity/v1";

    // Public endpoints
    String GAMES = BASE + "/games";
    String GAME_INFO = BASE + "/games/{gameId}/info";

    // Buyer endpoints (JWT required)
    String PARTICIPATE = BASE + "/games/{gameId}/participate";
    String MY_HISTORY = BASE + "/games/{gameId}/my-history";

    // Admin endpoints
    String ADMIN_CREATE_GAME = BASE + "/admin/games";
    String ADMIN_UPDATE_GAME = BASE + "/admin/games/{gameId}";
    String ADMIN_ACTIVATE_GAME = BASE + "/admin/games/{gameId}/activate";
    String ADMIN_END_GAME = BASE + "/admin/games/{gameId}/end";
    String ADMIN_ADD_PRIZE = BASE + "/admin/games/{gameId}/prizes";
    String ADMIN_GAME_STATS = BASE + "/admin/games/{gameId}/stats";

    // Internal endpoints
    String INTERNAL_ACTIVE_GAMES = "/internal/activity/games/active";

    // --- Request records ---

    record ParticipateRequest(String payload) {}

    record CreateGameRequest(
        @NotBlank String type,
        @NotBlank String name,
        String config,
        @Min(0) int perUserDailyLimit,
        @Min(0) int perUserTotalLimit
    ) {}

    record UpdateGameRequest(
        @NotBlank String name,
        String config,
        @Min(0) int perUserDailyLimit,
        @Min(0) int perUserTotalLimit
    ) {}

    record AddPrizeRequest(
        @NotBlank String name,
        @NotBlank String type,
        @NotNull BigDecimal value,
        @NotNull BigDecimal probability,
        int totalStock,
        int displayOrder,
        String imageUrl,
        String couponTemplateId
    ) {}

    // --- Response records ---

    record GameResponse(
        String id,
        String type,
        String name,
        String status,
        Instant startAt,
        Instant endAt,
        int participantCount
    ) {}

    record GameDetailResponse(
        GameResponse game,
        java.util.List<PrizeResponse> prizes
    ) {}

    record PrizeResponse(
        String id,
        String name,
        String type,
        BigDecimal value,
        String imageUrl,
        int displayOrder
    ) {}

    record ParticipateResponse(
        boolean win,
        String prizeId,
        String prizeName,
        String prizeType,
        BigDecimal prizeValue,
        String animationHint,
        String message
    ) {}

    record ParticipationResponse(
        String id,
        String result,
        String prizeId,
        String prizeSnapshot,
        String rewardStatus,
        Instant participatedAt
    ) {}

    record GameStatsResponse(
        String gameId,
        String gameName,
        String status,
        int participantCount,
        int remainingPrizeStock
    ) {}
}
