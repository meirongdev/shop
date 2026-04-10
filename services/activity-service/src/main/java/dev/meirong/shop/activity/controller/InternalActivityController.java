package dev.meirong.shop.activity.controller;

import dev.meirong.shop.activity.service.ActivityAdminService;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.activity.ActivityApi;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
public class InternalActivityController {

    private final ActivityAdminService adminService;

    public InternalActivityController(ActivityAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping(ActivityApi.INTERNAL_ACTIVE_GAMES)
    public ApiResponse<List<ActivityApi.GameResponse>> getActiveGames() {
        return ApiResponse.success(adminService.getActiveGames(Instant.now()).stream()
                .map(g -> new ActivityApi.GameResponse(g.getId(), g.getType().name(), g.getName(),
                        g.getStatus().name(), g.getStartAt(), g.getEndAt(), g.getParticipantCount()))
                .toList());
    }
}
