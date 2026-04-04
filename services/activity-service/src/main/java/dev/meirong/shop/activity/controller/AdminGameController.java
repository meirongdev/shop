package dev.meirong.shop.activity.controller;

import dev.meirong.shop.activity.domain.ActivityGame;
import dev.meirong.shop.activity.domain.ActivityRewardPrize;
import dev.meirong.shop.activity.domain.GameType;
import dev.meirong.shop.activity.domain.PrizeType;
import dev.meirong.shop.activity.service.ActivityAdminService;
import dev.meirong.shop.activity.service.ActivityQueryService;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.common.http.TrustedHeaderNames;
import dev.meirong.shop.contracts.api.ActivityApi;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class AdminGameController {

    private final ActivityAdminService adminService;
    private final ActivityQueryService queryService;

    public AdminGameController(ActivityAdminService adminService,
                                ActivityQueryService queryService) {
        this.adminService = adminService;
        this.queryService = queryService;
    }

    @PostMapping(ActivityApi.ADMIN_CREATE_GAME)
    public ApiResponse<ActivityApi.GameResponse> createGame(
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
            @RequestBody ActivityApi.CreateGameRequest request) {
        requireAdmin(headerRoles);
        ActivityGame game = adminService.createGame(
                GameType.valueOf(request.type()), request.name(), request.config(),
                request.perUserDailyLimit(), request.perUserTotalLimit(), "admin");
        return ApiResponse.success(toResponse(game));
    }

    @PutMapping(ActivityApi.ADMIN_UPDATE_GAME)
    public ApiResponse<ActivityApi.GameResponse> updateGame(
            @PathVariable String gameId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
            @RequestBody ActivityApi.UpdateGameRequest request) {
        requireAdmin(headerRoles);
        ActivityGame game = adminService.updateGame(gameId, request.name(), request.config(),
                request.perUserDailyLimit(), request.perUserTotalLimit());
        return ApiResponse.success(toResponse(game));
    }

    @PostMapping(ActivityApi.ADMIN_ACTIVATE_GAME)
    public ApiResponse<ActivityApi.GameResponse> activateGame(
            @PathVariable String gameId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles) {
        requireAdmin(headerRoles);
        ActivityGame game = adminService.activateGame(gameId);
        return ApiResponse.success(toResponse(game));
    }

    @PostMapping(ActivityApi.ADMIN_END_GAME)
    public ApiResponse<ActivityApi.GameResponse> endGame(
            @PathVariable String gameId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles) {
        requireAdmin(headerRoles);
        ActivityGame game = adminService.endGame(gameId);
        return ApiResponse.success(toResponse(game));
    }

    @PostMapping(ActivityApi.ADMIN_ADD_PRIZE)
    public ApiResponse<ActivityApi.PrizeResponse> addPrize(
            @PathVariable String gameId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles,
            @RequestBody ActivityApi.AddPrizeRequest request) {
        requireAdmin(headerRoles);
        ActivityRewardPrize prize = new ActivityRewardPrize(
                UUID.randomUUID().toString(), gameId, request.name(),
                PrizeType.valueOf(request.type()));
        prize.setValue(request.value());
        prize.setProbability(request.probability());
        prize.setTotalStock(request.totalStock());
        prize.setRemainingStock(request.totalStock());
        prize.setDisplayOrder(request.displayOrder());
        prize.setImageUrl(request.imageUrl());
        prize.setCouponTemplateId(request.couponTemplateId());
        ActivityRewardPrize saved = adminService.addPrize(gameId, prize);
        return ApiResponse.success(new ActivityApi.PrizeResponse(
                saved.getId(), saved.getName(), saved.getType().name(),
                saved.getValue(), saved.getImageUrl(), saved.getDisplayOrder()));
    }

    @GetMapping(ActivityApi.ADMIN_GAME_STATS)
    public ApiResponse<ActivityApi.GameStatsResponse> getStats(
            @PathVariable String gameId,
            @RequestHeader(value = TrustedHeaderNames.ROLES, required = false) String headerRoles) {
        requireAdmin(headerRoles);
        ActivityGame game = queryService.getGame(gameId);
        List<ActivityRewardPrize> prizes = adminService.getPrizes(gameId);
        int totalRemaining = prizes.stream().mapToInt(ActivityRewardPrize::getRemainingStock).sum();
        return ApiResponse.success(new ActivityApi.GameStatsResponse(
                game.getId(), game.getName(), game.getStatus().name(),
                game.getParticipantCount(), totalRemaining));
    }

    private void requireAdmin(String headerRoles) {
        List<String> roles = headerRoles == null
                ? List.of()
                : List.of(headerRoles.split(",")).stream()
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .toList();
        if (!roles.contains("ROLE_ADMIN")) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "Activity admin requires ADMIN role");
        }
    }

    private ActivityApi.GameResponse toResponse(ActivityGame g) {
        return new ActivityApi.GameResponse(g.getId(), g.getType().name(), g.getName(),
                g.getStatus().name(), g.getStartAt(), g.getEndAt(), g.getParticipantCount());
    }
}
