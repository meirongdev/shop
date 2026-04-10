package dev.meirong.shop.search.service;

import dev.meirong.shop.contracts.search.SearchApi;
import dev.meirong.shop.search.config.SearchProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SearchQueryAnalyticsService {

    private final Duration retention;
    private final int maxTrackedQueries;
    private final int minimumTrendingSearches;
    private final Clock clock;
    private final ConcurrentHashMap<String, QueryStat> stats = new ConcurrentHashMap<>();

    @Autowired
    public SearchQueryAnalyticsService(SearchProperties properties) {
        this(properties.analytics(), Clock.systemUTC());
    }

    SearchQueryAnalyticsService(SearchProperties.AnalyticsProperties analytics, Clock clock) {
        this.retention = analytics.retention();
        this.maxTrackedQueries = analytics.maxTrackedQueries();
        this.minimumTrendingSearches = analytics.minimumTrendingSearches();
        this.clock = clock;
    }

    public void recordQuery(String rawQuery) {
        String normalizedKey = normalizeKey(rawQuery);
        String displayQuery = normalizeDisplay(rawQuery);
        if (normalizedKey == null || displayQuery == null) {
            return;
        }

        Instant now = clock.instant();
        pruneExpired(now);
        stats.compute(normalizedKey, (ignored, existing) -> {
            QueryStat stat = existing == null ? new QueryStat(displayQuery, now) : existing;
            stat.record(displayQuery, now);
            return stat;
        });
        trimIfNeeded();
    }

    public SearchApi.TrendingQueriesResponse trending(int requestedLimit) {
        Instant now = clock.instant();
        pruneExpired(now);

        int limit = clamp(requestedLimit, 1, 10);
        var queries = stats.values().stream()
                .filter(stat -> stat.searches() >= minimumTrendingSearches)
                .sorted(Comparator.comparingLong(QueryStat::searches)
                        .reversed()
                        .thenComparing(QueryStat::lastSearchedAt, Comparator.reverseOrder()))
                .limit(limit)
                .map(stat -> new SearchApi.TrendingQuery(
                        stat.displayQuery(),
                        stat.searches(),
                        stat.lastSearchedAt()))
                .toList();

        return new SearchApi.TrendingQueriesResponse(queries);
    }

    private void pruneExpired(Instant now) {
        Instant cutoff = now.minus(retention);
        stats.entrySet().removeIf(entry -> entry.getValue().lastSearchedAt().isBefore(cutoff));
    }

    private void trimIfNeeded() {
        int overflow = stats.size() - maxTrackedQueries;
        if (overflow <= 0) {
            return;
        }

        var removableKeys = stats.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, QueryStat> entry) -> entry.getValue().searches())
                        .thenComparing(entry -> entry.getValue().lastSearchedAt()))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList();
        removableKeys.forEach(stats::remove);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeKey(String rawQuery) {
        String display = normalizeDisplay(rawQuery);
        return display == null ? null : display.toLowerCase(Locale.ROOT);
    }

    private static String normalizeDisplay(String rawQuery) {
        if (rawQuery == null) {
            return null;
        }
        String normalized = rawQuery.trim().replaceAll("\\s+", " ");
        return normalized.length() < 2 ? null : normalized;
    }

    static final class QueryStat {

        private final AtomicLong searches = new AtomicLong();
        private final AtomicReference<String> displayQuery;
        private final AtomicReference<Instant> lastSearchedAt;

        QueryStat(String displayQuery, Instant now) {
            this.displayQuery = new AtomicReference<>(displayQuery);
            this.lastSearchedAt = new AtomicReference<>(now);
        }

        void record(String latestDisplayQuery, Instant now) {
            displayQuery.set(latestDisplayQuery);
            lastSearchedAt.set(now);
            searches.incrementAndGet();
        }

        long searches() {
            return searches.get();
        }

        String displayQuery() {
            return displayQuery.get();
        }

        Instant lastSearchedAt() {
            return lastSearchedAt.get();
        }
    }
}
