package dev.meirong.shop.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.meirong.shop.search.config.SearchProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class SearchQueryAnalyticsServiceTest {

    @Test
    void trending_normalizesQueriesAndOrdersByCountThenRecency() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-22T00:00:00Z"));
        SearchQueryAnalyticsService service = new SearchQueryAnalyticsService(
                new SearchProperties.AnalyticsProperties(Duration.ofDays(7), 100, 2),
                clock
        );

        service.recordQuery("Alpha Phone");
        service.recordQuery("Alpha   Phone");
        clock.advance(Duration.ofMinutes(1));
        service.recordQuery("Beta Serum");
        service.recordQuery("Beta Serum");
        service.recordQuery("Beta Serum");

        var response = service.trending(5);

        assertThat(response.queries())
                .extracting(query -> query.query() + ":" + query.searches())
                .containsExactly("Beta Serum:3", "Alpha Phone:2");
    }

    @Test
    void trending_prunesExpiredQueries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-22T00:00:00Z"));
        SearchQueryAnalyticsService service = new SearchQueryAnalyticsService(
                new SearchProperties.AnalyticsProperties(Duration.ofHours(1), 100, 1),
                clock
        );

        service.recordQuery("Old Query");
        clock.advance(Duration.ofHours(2));
        service.recordQuery("Fresh Query");

        var response = service.trending(5);

        assertThat(response.queries()).extracting(query -> query.query()).containsExactly("Fresh Query");
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
