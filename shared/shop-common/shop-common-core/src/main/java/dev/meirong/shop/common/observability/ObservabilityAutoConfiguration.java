package dev.meirong.shop.common.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.pyroscope.http.Format;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.config.Config;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ProfilingProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnClass(OpenTelemetryAppender.class)
    @ConditionalOnProperty(prefix = "management.otlp.logging", name = "endpoint")
    InitializingBean openTelemetryAppenderInitializer(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }

    @Bean
    @ConditionalOnClass(PyroscopeAgent.class)
    @ConditionalOnProperty(prefix = "shop.profiling", name = "enabled", havingValue = "true")
    ApplicationListener<ApplicationReadyEvent> pyroscopeStarter(ProfilingProperties properties,
                                                                @Value("${spring.application.name:unknown}") String applicationName) {
        return event -> PyroscopeAgent.start(new Config.Builder()
                .setApplicationName(applicationName)
                .setProfilingEvent(EventType.ITIMER)
                .setProfilingAlloc("512k")
                .setFormat(Format.JFR)
                .setServerAddress(properties.serverAddress())
                .build());
    }
}
