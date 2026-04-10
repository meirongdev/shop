package dev.meirong.shop.webhook.service;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import dev.meirong.shop.contracts.webhook.WebhookApi;
import dev.meirong.shop.webhook.domain.WebhookDeliveryEntity;
import dev.meirong.shop.webhook.domain.WebhookDeliveryRepository;
import dev.meirong.shop.webhook.domain.WebhookEndpointEntity;
import dev.meirong.shop.webhook.domain.WebhookEndpointRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookApplicationService {

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryRepository deliveryRepository;

    public WebhookApplicationService(WebhookEndpointRepository endpointRepository,
                                      WebhookDeliveryRepository deliveryRepository) {
        this.endpointRepository = endpointRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public WebhookApi.EndpointResponse createEndpoint(WebhookApi.CreateEndpointRequest request) {
        String secret = "whsec_" + UUID.randomUUID().toString().replace("-", "");
        WebhookEndpointEntity entity = WebhookEndpointEntity.create(
                request.sellerId(), request.url(), secret,
                Set.copyOf(request.eventTypes()), request.description());
        return toResponse(endpointRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<WebhookApi.EndpointResponse> listEndpoints(String sellerId) {
        return endpointRepository.findBySellerId(sellerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WebhookApi.EndpointResponse updateEndpoint(WebhookApi.UpdateEndpointRequest request) {
        WebhookEndpointEntity entity = endpointRepository.findById(request.endpointId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                        "Webhook endpoint not found: " + request.endpointId()));
        entity.update(request.url(), Set.copyOf(request.eventTypes()), request.description());
        return toResponse(endpointRepository.save(entity));
    }

    @Transactional
    public void deleteEndpoint(String endpointId) {
        WebhookEndpointEntity entity = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                        "Webhook endpoint not found: " + endpointId));
        entity.deactivate();
        endpointRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<WebhookApi.DeliveryResponse> listDeliveries(String endpointId) {
        return deliveryRepository.findByEndpointId(endpointId).stream()
                .map(this::toDeliveryResponse)
                .toList();
    }

    private WebhookApi.EndpointResponse toResponse(WebhookEndpointEntity entity) {
        return new WebhookApi.EndpointResponse(
                entity.getId(), entity.getSellerId(), entity.getUrl(),
                entity.getSecret(), entity.getEventTypesSet().stream().toList(),
                entity.isActive(), entity.getDescription(),
                entity.getCreatedAt());
    }

    private WebhookApi.DeliveryResponse toDeliveryResponse(WebhookDeliveryEntity entity) {
        return new WebhookApi.DeliveryResponse(
                entity.getId(), entity.getEndpointId(), entity.getEventType(),
                entity.getEventId(), entity.getStatus(), entity.getResponseCode(),
                entity.getAttemptCount(), entity.getCreatedAt());
    }
}
