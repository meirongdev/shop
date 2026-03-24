package ${package}.messaging;

import ${package}.service.TemplateWorkerService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SampleEventListener {

    private final TemplateWorkerService service;

    public SampleEventListener(TemplateWorkerService service) {
        this.service = service;
    }

    @KafkaListener(topics = "${artifactId}.events.v1", groupId = "${artifactId}")
    public void onMessage(String payload) {
        service.record("sample-event", payload);
    }
}
