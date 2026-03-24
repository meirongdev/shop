package dev.meirong.shop.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meirong.shop.webhook.domain.WebhookDeliveryEntity;
import dev.meirong.shop.webhook.domain.WebhookEndpointEntity;
import dev.meirong.shop.webhook.service.WebhookSigner;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WebhookDomainTest {

    @Test
    void endpointEntity_matchesEventType() {
        WebhookEndpointEntity entity = WebhookEndpointEntity.create(
                "seller-1", "https://a.com", "secret",
                Set.of("order.paid", "order.shipped"), "desc");

        assertThat(entity.matchesEventType("order.paid")).isTrue();
        assertThat(entity.matchesEventType("order.shipped")).isTrue();
        assertThat(entity.matchesEventType("order.cancelled")).isFalse();
    }

    @Test
    void endpointEntity_inactiveDoesNotMatch() {
        WebhookEndpointEntity entity = WebhookEndpointEntity.create(
                "seller-1", "https://a.com", "secret",
                Set.of("order.paid"), "desc");
        entity.deactivate();

        assertThat(entity.matchesEventType("order.paid")).isFalse();
    }

    @Test
    void deliveryEntity_markSuccess() {
        WebhookDeliveryEntity delivery = WebhookDeliveryEntity.create(
                "ep1", "order.paid", "evt1", "{}");

        delivery.markSuccess(200, "OK");

        assertThat(delivery.getStatus()).isEqualTo("SUCCESS");
        assertThat(delivery.getResponseCode()).isEqualTo(200);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void deliveryEntity_markFailed_retriesWithBackoff() {
        WebhookDeliveryEntity delivery = WebhookDeliveryEntity.create(
                "ep1", "order.paid", "evt1", "{}");

        delivery.markFailed(500, "Internal Server Error", 5, 60);

        assertThat(delivery.getStatus()).isEqualTo("RETRYING");
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextRetryAt()).isNotNull();
    }

    @Test
    void deliveryEntity_markFailed_exhaustsRetries() {
        WebhookDeliveryEntity delivery = WebhookDeliveryEntity.create(
                "ep1", "order.paid", "evt1", "{}");
        // Exhaust all attempts
        for (int i = 0; i < 4; i++) {
            delivery.markFailed(500, "Error", 5, 60);
        }
        assertThat(delivery.getStatus()).isEqualTo("RETRYING");

        delivery.markFailed(500, "Error", 5, 60);
        assertThat(delivery.getStatus()).isEqualTo("FAILED");
        assertThat(delivery.getAttemptCount()).isEqualTo(5);
    }

    @Test
    void signer_producesConsistentSignature() {
        String sig1 = WebhookSigner.sign("{\"test\":1}", "my-secret");
        String sig2 = WebhookSigner.sign("{\"test\":1}", "my-secret");

        assertThat(sig1).startsWith("sha256=");
        assertThat(sig1).isEqualTo(sig2);
    }

    @Test
    void signer_differentSecretProducesDifferentSignature() {
        String sig1 = WebhookSigner.sign("{\"test\":1}", "secret-a");
        String sig2 = WebhookSigner.sign("{\"test\":1}", "secret-b");

        assertThat(sig1).isNotEqualTo(sig2);
    }
}
