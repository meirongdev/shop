package ${package}.service;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GatewayTemplateService {

    public GatewayStatusResponse buildResponse() {
        return new GatewayStatusResponse("${artifactId}", "ready", Instant.now(),
                List.of("/gateway/v1/route-ping", "/actuator/health/readiness"));
    }

    public record GatewayStatusResponse(String service, String status, Instant checkedAt, List<String> sampleRoutes) {
    }
}
