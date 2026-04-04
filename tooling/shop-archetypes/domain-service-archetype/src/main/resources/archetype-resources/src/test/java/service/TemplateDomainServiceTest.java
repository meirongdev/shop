package ${package}.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TemplateDomainServiceTest {

    private final TemplateDomainService service = new TemplateDomainService();

    @Test
    void buildResponseReferencesMigrationTable() {
        var response = service.buildResponse();

        assertThat(response.tableName()).isEqualTo("sample_aggregate");
        assertThat(response.nextStep()).contains("migrate");
    }
}
