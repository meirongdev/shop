package ${package}.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.contracts.event.EventEnvelope;
import ${package}.service.TemplateWorkerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SampleEventListener {

    private final TemplateWorkerService service;
    private final ObjectMapper objectMapper;

    public SampleEventListener(TemplateWorkerService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${artifactId}.events.v1", groupId = "${artifactId}")
    public void onMessage(String payload) throws Exception {
        EventEnvelope<Object> envelope = objectMapper.readValue(payload, new TypeReference<>() {});
        envelope.assertSupportedSchema(EventEnvelope.CURRENT_SCHEMA_VERSION);
        service.record("sample-event", payload);
    }
}
