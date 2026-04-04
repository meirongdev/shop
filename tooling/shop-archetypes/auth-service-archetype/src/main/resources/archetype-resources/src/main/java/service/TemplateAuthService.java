package ${package}.service;

import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class TemplateAuthService {

    public TemplateAuthResponse buildResponse() {
        return new TemplateAuthResponse("${artifactId}", "Replace this skeleton with JWT issuance and identity federation.", Instant.now());
    }

    public record TemplateAuthResponse(String service, String nextStep, Instant generatedAt) {
    }
}
