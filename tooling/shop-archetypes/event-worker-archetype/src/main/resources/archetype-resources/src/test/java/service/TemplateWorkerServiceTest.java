package ${package}.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ${package}.domain.EventCheckpointRepository;
import org.junit.jupiter.api.Test;

class TemplateWorkerServiceTest {

    @Test
    void buildResponseExposesTopic() {
        var service = new TemplateWorkerService(mock(EventCheckpointRepository.class));

        var response = service.buildResponse();

        assertThat(response.topic()).isEqualTo("${artifactId}.events.v1");
    }
}
