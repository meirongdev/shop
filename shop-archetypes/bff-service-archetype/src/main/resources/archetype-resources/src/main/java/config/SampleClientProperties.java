package ${package}.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shop.clients")
public record SampleClientProperties(Sample sample) {

    public record Sample(String baseUrl) {
    }
}
