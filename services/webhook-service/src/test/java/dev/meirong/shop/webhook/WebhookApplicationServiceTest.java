package dev.meirong.shop.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.contracts.webhook.WebhookApi;
import dev.meirong.shop.webhook.domain.WebhookDeliveryEntity;
import dev.meirong.shop.webhook.domain.WebhookDeliveryRepository;
import dev.meirong.shop.webhook.domain.WebhookEndpointEntity;
import dev.meirong.shop.webhook.domain.WebhookEndpointRepository;
import dev.meirong.shop.webhook.service.WebhookApplicationService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebhookApplicationServiceTest {

    @Mock
    private WebhookEndpointRepository endpointRepository;

    @Mock
    private WebhookDeliveryRepository deliveryRepository;

    @InjectMocks
    private WebhookApplicationService service;

    @Test
    void createEndpoint_generatesSecretAndPersists() {
        WebhookApi.CreateEndpointRequest request = new WebhookApi.CreateEndpointRequest(
                "seller-2001", "https://example.com/webhook",
                List.of("order.paid", "order.shipped"), "My webhook");
        when(endpointRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        WebhookApi.EndpointResponse response = service.createEndpoint(request);

        assertThat(response.sellerId()).isEqualTo("seller-2001");
        assertThat(response.url()).isEqualTo("https://example.com/webhook");
        assertThat(response.secret()).startsWith("whsec_");
        assertThat(response.eventTypes()).containsExactlyInAnyOrder("order.paid", "order.shipped");
        assertThat(response.active()).isTrue();
    }

    @Test
    void listEndpoints_returnsSellerEndpoints() {
        WebhookEndpointEntity entity = WebhookEndpointEntity.create(
                "seller-2001", "https://a.com/hook", "secret",
                Set.of("order.paid"), "test");
        when(endpointRepository.findBySellerId("seller-2001")).thenReturn(List.of(entity));

        List<WebhookApi.EndpointResponse> result = service.listEndpoints("seller-2001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sellerId()).isEqualTo("seller-2001");
    }

    @Test
    void deleteEndpoint_deactivatesIt() {
        WebhookEndpointEntity entity = WebhookEndpointEntity.create(
                "seller-2001", "https://a.com/hook", "secret",
                Set.of("order.paid"), "test");
        when(endpointRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(endpointRepository.save(entity)).thenReturn(entity);

        service.deleteEndpoint(entity.getId());

        assertThat(entity.isActive()).isFalse();
    }

    @Test
    void deleteEndpoint_notFound_throws() {
        when(endpointRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteEndpoint("missing"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Webhook endpoint not found");
    }
}
