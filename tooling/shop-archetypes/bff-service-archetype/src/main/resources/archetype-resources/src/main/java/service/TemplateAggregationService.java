package ${package}.service;

import ${package}.config.SampleClientProperties;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.stereotype.Service;

@Service
public class TemplateAggregationService {

    private final SampleClientProperties properties;

    public TemplateAggregationService(SampleClientProperties properties) {
        this.properties = properties;
    }

    public TemplateBffResponse buildResponse() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> downstream = executor.submit(() -> properties.sample().baseUrl());
            Future<String> docs = executor.submit(() -> "/swagger-ui.html");
            return new TemplateBffResponse("${artifactId}", List.of(
                    new DashboardCard("downstreamBaseUrl", downstream.get()),
                    new DashboardCard("openApi", docs.get())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while assembling template cards", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to assemble template cards", e);
        }
    }

    public record TemplateBffResponse(String service, List<DashboardCard> cards) {
    }

    public record DashboardCard(String title, String value) {
    }
}
