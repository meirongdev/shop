package dev.meirong.shop.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdExtractorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void returnsTraceIdFromMdcWhenPresent() {
        MDC.put("traceId", "trace-123");

        assertThat(TraceIdExtractor.currentTraceId()).isEqualTo("trace-123");
    }

    @Test
    void returnsEmptyStringWhenTraceIdIsMissing() {
        assertThat(TraceIdExtractor.currentTraceId()).isEmpty();
    }
}
