package ${package}.service;

import org.springframework.stereotype.Service;

@Service
public class TemplateDomainService {

    public TemplateDomainResponse buildResponse() {
        return new TemplateDomainResponse("${artifactId}", "sample_aggregate", "validate + migrate + expose API");
    }

    public record TemplateDomainResponse(String service, String tableName, String nextStep) {
    }
}
