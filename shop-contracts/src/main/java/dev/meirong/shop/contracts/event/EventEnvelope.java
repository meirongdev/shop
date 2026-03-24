package dev.meirong.shop.contracts.event;

import java.time.Instant;

public record EventEnvelope<T>(String eventId,
                               String source,
                               String type,
                               Instant timestamp,
                               T data) {
}
