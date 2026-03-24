package dev.meirong.shop.subscription.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.api.SubscriptionApi;
import dev.meirong.shop.subscription.service.SubscriptionApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SubscriptionApi.BASE_PATH)
public class SubscriptionController {

    private final SubscriptionApplicationService service;

    public SubscriptionController(SubscriptionApplicationService service) {
        this.service = service;
    }

    @PostMapping("/plan/create")
    public ApiResponse<SubscriptionApi.PlanResponse> createPlan(
            @Valid @RequestBody SubscriptionApi.CreatePlanRequest request) {
        return ApiResponse.success(service.createPlan(request));
    }

    @PostMapping("/plan/list")
    public ApiResponse<List<SubscriptionApi.PlanResponse>> listPlans(
            @Valid @RequestBody SubscriptionApi.ListPlansRequest request) {
        return ApiResponse.success(service.listPlans(request.sellerId()));
    }

    @PostMapping("/plan/get")
    public ApiResponse<SubscriptionApi.PlanResponse> getPlan(
            @Valid @RequestBody SubscriptionApi.GetPlanRequest request) {
        return ApiResponse.success(service.getPlan(request.planId()));
    }

    @PostMapping("/subscribe")
    public ApiResponse<SubscriptionApi.SubscriptionResponse> subscribe(
            @Valid @RequestBody SubscriptionApi.SubscribeRequest request) {
        return ApiResponse.success(service.subscribe(request));
    }

    @PostMapping("/list")
    public ApiResponse<List<SubscriptionApi.SubscriptionResponse>> listSubscriptions(
            @Valid @RequestBody SubscriptionApi.ListSubscriptionsRequest request) {
        return ApiResponse.success(service.listSubscriptions(request.buyerId()));
    }

    @PostMapping("/pause")
    public ApiResponse<SubscriptionApi.SubscriptionResponse> pause(
            @Valid @RequestBody SubscriptionApi.SubscriptionActionRequest request) {
        return ApiResponse.success(service.pause(request.subscriptionId()));
    }

    @PostMapping("/resume")
    public ApiResponse<SubscriptionApi.SubscriptionResponse> resume(
            @Valid @RequestBody SubscriptionApi.SubscriptionActionRequest request) {
        return ApiResponse.success(service.resume(request.subscriptionId()));
    }

    @PostMapping("/cancel")
    public ApiResponse<SubscriptionApi.SubscriptionResponse> cancel(
            @Valid @RequestBody SubscriptionApi.SubscriptionActionRequest request) {
        return ApiResponse.success(service.cancel(request.subscriptionId()));
    }
}
