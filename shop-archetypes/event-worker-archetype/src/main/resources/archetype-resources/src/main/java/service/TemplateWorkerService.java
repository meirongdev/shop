package ${package}.service;

import ${package}.domain.EventCheckpointEntity;
import ${package}.domain.EventCheckpointRepository;
import org.springframework.stereotype.Service;

@Service
public class TemplateWorkerService {

    private final EventCheckpointRepository repository;

    public TemplateWorkerService(EventCheckpointRepository repository) {
        this.repository = repository;
    }

    public WorkerStatusResponse buildResponse() {
        return new WorkerStatusResponse("${artifactId}", "${artifactId}.events.v1", "Persist checkpoints before calling downstream systems.");
    }

    public void record(String eventKey, String payload) {
        repository.save(new EventCheckpointEntity(eventKey, payload));
    }

    public record WorkerStatusResponse(String service, String topic, String nextStep) {
    }
}
