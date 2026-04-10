package dev.meirong.shop.contracts.webhook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;

public final class WebhookApi {

    public static final String BASE_PATH = "/webhook/v1";
    public static final String ENDPOINT_CREATE = BASE_PATH + "/endpoint/create";
    public static final String ENDPOINT_LIST = BASE_PATH + "/endpoint/list";
    public static final String ENDPOINT_UPDATE = BASE_PATH + "/endpoint/update";
    public static final String ENDPOINT_DELETE = BASE_PATH + "/endpoint/delete";
    public static final String DELIVERY_LIST = BASE_PATH + "/delivery/list";

    /** Supported event types for webhook subscriptions. */
    public static final List<String> SUPPORTED_EVENT_TYPES = List.of(
            "order.created", "order.paid", "order.shipped", "order.delivered",
            "order.completed", "order.cancelled", "order.refund_requested",
            "order.refund_approved", "order.refund_rejected",
            "wallet.deposit", "wallet.withdrawal",
            "user.registered"
    );

    private WebhookApi() {
    }

    public record CreateEndpointRequest(
            @NotBlank String sellerId,
            @NotBlank String url,
            @NotEmpty List<String> eventTypes,
            String description
    ) {
    }

    public record ListEndpointsRequest(@NotBlank String sellerId) {
    }

    public record UpdateEndpointRequest(
            @NotBlank String endpointId,
            @NotBlank String url,
            @NotEmpty List<String> eventTypes,
            String description
    ) {
    }

    public record DeleteEndpointRequest(@NotBlank String endpointId) {
    }

    public record ListDeliveriesRequest(@NotBlank String endpointId) {
    }

    public record EndpointResponse(
            String id,
            String sellerId,
            String url,
            String secret,
            List<String> eventTypes,
            boolean active,
            String description,
            Instant createdAt
    ) {
    }

    public record DeliveryResponse(
            String id,
            String endpointId,
            String eventType,
            String eventId,
            String status,
            Integer responseCode,
            int attemptCount,
            Instant createdAt
    ) {
    }
}
