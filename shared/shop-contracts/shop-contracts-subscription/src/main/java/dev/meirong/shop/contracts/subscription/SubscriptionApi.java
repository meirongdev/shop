package dev.meirong.shop.contracts.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SubscriptionApi {

    public static final String BASE_PATH = "/subscription/v1";
    public static final String PLAN_CREATE = BASE_PATH + "/plan/create";
    public static final String PLAN_LIST = BASE_PATH + "/plan/list";
    public static final String PLAN_GET = BASE_PATH + "/plan/get";
    public static final String SUBSCRIBE = BASE_PATH + "/subscribe";
    public static final String LIST_MY = BASE_PATH + "/list";
    public static final String PAUSE = BASE_PATH + "/pause";
    public static final String RESUME = BASE_PATH + "/resume";
    public static final String CANCEL = BASE_PATH + "/cancel";

    public static final List<String> FREQUENCIES = List.of("WEEKLY", "BIWEEKLY", "MONTHLY", "QUARTERLY");

    private SubscriptionApi() {
    }

    public record CreatePlanRequest(
            @NotBlank String sellerId,
            @NotBlank String productId,
            @NotBlank String name,
            String description,
            @NotNull @Positive BigDecimal price,
            @NotBlank String frequency
    ) {
    }

    public record ListPlansRequest(@NotBlank String sellerId) {
    }

    public record GetPlanRequest(@NotBlank String planId) {
    }

    public record SubscribeRequest(
            @NotBlank String buyerId,
            @NotBlank String planId,
            @Positive int quantity
    ) {
    }

    public record ListSubscriptionsRequest(@NotBlank String buyerId) {
    }

    public record SubscriptionActionRequest(@NotBlank String subscriptionId) {
    }

    public record PlanResponse(
            String id,
            String sellerId,
            String productId,
            String name,
            String description,
            BigDecimal price,
            String frequency,
            boolean active,
            Instant createdAt
    ) {
    }

    public record SubscriptionResponse(
            String id,
            String buyerId,
            String planId,
            String status,
            int quantity,
            Instant nextRenewalAt,
            String lastOrderId,
            int totalRenewals,
            Instant createdAt
    ) {
    }
}
