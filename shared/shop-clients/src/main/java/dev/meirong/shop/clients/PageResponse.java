package dev.meirong.shop.clients;

import java.util.List;

/**
 * Generic paginated response wrapper used by loyalty-service endpoints.
 */
public record PageResponse<T>(List<T> content, int page, int size, long total) {
}
