package ${package}.service;

import static org.assertj.core.api.Assertions.assertThat;

import ${package}.config.SampleClientProperties;
import org.junit.jupiter.api.Test;

class TemplateAggregationServiceTest {

    @Test
    void buildResponseCollectsTwoCards() {
        var properties = new SampleClientProperties(new SampleClientProperties.Sample("http://localhost:8080"));
        var service = new TemplateAggregationService(properties);

        var response = service.buildResponse();

        assertThat(response.cards()).hasSize(2);
        assertThat(response.cards().get(0).value()).isEqualTo("http://localhost:8080");
    }
}
