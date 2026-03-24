package dev.meirong.shop.activity.controller;

import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.activity.domain.ActivityParticipation;
import dev.meirong.shop.activity.domain.ActivityRewardPrize;
import dev.meirong.shop.activity.engine.GameEngine;
import dev.meirong.shop.activity.engine.ParticipateResult;
import dev.meirong.shop.activity.service.ActivityQueryService;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.http.TrustedHeaderNames;
import dev.meirong.shop.contracts.api.ActivityApi;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
public class ActivityController {

    private static final String DEVICE_FINGERPRINT = "X-Device-Fingerprint";

    private final GameEngine gameEngine;
    private final ActivityQueryService queryService;

    public ActivityController(GameEngine gameEngine, ActivityQueryService queryService) {
        this.gameEngine = gameEngine;
        this.queryService = queryService;
    }

    @GetMapping(ActivityApi.GAMES)
    public ApiResponse<List<ActivityApi.GameResponse>> listActiveGames() {
        return ApiResponse.success(queryService.getActiveGames(Instant.now()).stream()
                .map(this::toGameResponse).toList());
    }

    @GetMapping(ActivityApi.GAME_INFO)
    public ApiResponse<ActivityApi.GameDetailResponse> getGameInfo(@PathVariable String gameId) {
        ActivityGame game = queryService.getGame(gameId);
        List<ActivityRewardPrize> prizes = queryService.getPrizes(gameId);
        List<ActivityApi.PrizeResponse> prizeResponses = prizes.stream()
                .map(p -> new ActivityApi.PrizeResponse(p.getId(), p.getName(), p.getType().name(),
                        p.getValue(), p.getImageUrl(), p.getDisplayOrder()))
                .toList();
        return ApiResponse.success(new ActivityApi.GameDetailResponse(
                toGameResponse(game), prizeResponses));
    }

    @PostMapping(ActivityApi.PARTICIPATE)
    public ApiResponse<ActivityApi.ParticipateResponse> participate(
            @PathVariable String gameId,
            @RequestBody(required = false) ActivityApi.ParticipateRequest request,
            @RequestHeader(TrustedHeaderNames.PLAYER_ID) String playerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress,
            @RequestHeader(value = DEVICE_FINGERPRINT, required = false) String deviceFingerprint) {
        requireSignedInBuyer(headerRoles, "Activity participation");
        String payload = request != null ? request.payload() : null;
        ParticipateResult result = gameEngine.participate(gameId, playerId, payload, ipAddress, deviceFingerprint);
        return ApiResponse.success(new ActivityApi.ParticipateResponse(
                result.win(), result.prizeId(), result.prizeName(),
                result.prizeType() != null ? result.prizeType().name() : null,
                result.prizeValue(), result.animationHint(), result.message()));
    }

    @GetMapping(ActivityApi.MY_HISTORY)
    public ApiResponse<List<ActivityApi.ParticipationResponse>> myHistory(
            @PathVariable String gameId,
            @RequestHeader(TrustedHeaderNames.PLAYER_ID) String playerId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles) {
        requireSignedInBuyer(headerRoles, "Activity history");
        List<ActivityParticipation> records = queryService.getParticipationHistory(gameId, playerId);
        return ApiResponse.success(records.stream().map(this::toParticipationResponse).toList());
    }

    private void requireSignedInBuyer(String headerRoles, String capability) {
        List<String> roles = headerRoles == null
                ? List.of()
                : List.of(headerRoles.split(",")).stream()
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .toList();
        if (roles.contains("ROLE_BUYER_GUEST") || !roles.contains("ROLE_BUYER")) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    capability + " requires a signed-in buyer account");
        }
    }

    private ActivityApi.GameResponse toGameResponse(ActivityGame g) {
        return new ActivityApi.GameResponse(g.getId(), g.getType().name(), g.getName(),
                g.getStatus().name(), g.getStartAt(), g.getEndAt(), g.getParticipantCount());
    }

    private ActivityApi.ParticipationResponse toParticipationResponse(ActivityParticipation p) {
        return new ActivityApi.ParticipationResponse(p.getId(), p.getResult(),
                p.getPrizeId(), p.getPrizeSnapshot(), p.getRewardStatus(), p.getParticipatedAt());
    }
}
