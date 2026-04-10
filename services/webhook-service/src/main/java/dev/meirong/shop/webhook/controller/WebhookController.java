package dev.meirong.shop.webhook.controller;

import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.contracts.webhook.WebhookApi;
import dev.meirong.shop.webhook.service.WebhookApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(WebhookApi.BASE_PATH)
public class WebhookController {

    private final WebhookApplicationService service;

    public WebhookController(WebhookApplicationService service) {
        this.service = service;
    }

    @PostMapping("/endpoint/create")
    public ApiResponse<WebhookApi.EndpointResponse> createEndpoint(
            @Valid @RequestBody WebhookApi.CreateEndpointRequest request) {
        return ApiResponse.success(service.createEndpoint(request));
    }

    @PostMapping("/endpoint/list")
    public ApiResponse<List<WebhookApi.EndpointResponse>> listEndpoints(
            @Valid @RequestBody WebhookApi.ListEndpointsRequest request) {
        return ApiResponse.success(service.listEndpoints(request.sellerId()));
    }

    @PostMapping("/endpoint/update")
    public ApiResponse<WebhookApi.EndpointResponse> updateEndpoint(
            @Valid @RequestBody WebhookApi.UpdateEndpointRequest request) {
        return ApiResponse.success(service.updateEndpoint(request));
    }

    @PostMapping("/endpoint/delete")
    public ApiResponse<Void> deleteEndpoint(
            @Valid @RequestBody WebhookApi.DeleteEndpointRequest request) {
        service.deleteEndpoint(request.endpointId());
        return ApiResponse.success(null);
    }

    @PostMapping("/delivery/list")
    public ApiResponse<List<WebhookApi.DeliveryResponse>> listDeliveries(
            @Valid @RequestBody WebhookApi.ListDeliveriesRequest request) {
        return ApiResponse.success(service.listDeliveries(request.endpointId()));
    }
}
