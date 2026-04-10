package dev.meirong.shop.contracts.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope<T>(String eventId,
                               String source,
                               String type,
                               Instant timestamp,
                               Integer schemaVersion,
                               String contentType,
                               T data) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String JSON_CONTENT_TYPE = "application/json";

    public EventEnvelope {
        schemaVersion = schemaVersion == null || schemaVersion < 1 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        contentType = contentType == null || contentType.isBlank() ? JSON_CONTENT_TYPE : contentType;
    }

    public EventEnvelope(String eventId,
                         String source,
                         String type,
                         Instant timestamp,
                         T data) {
        this(eventId, source, type, timestamp, CURRENT_SCHEMA_VERSION, JSON_CONTENT_TYPE, data);
    }

    public void assertSupportedSchema(int... supportedSchemaVersions) {
        if (supportedSchemaVersions == null || supportedSchemaVersions.length == 0) {
            throw new IllegalArgumentException("At least one supported schema version must be configured");
        }
        boolean supported = java.util.Arrays.stream(supportedSchemaVersions).anyMatch(version -> version == schemaVersion);
        if (!supported) {
            throw new IllegalArgumentException(
                    "Unsupported schemaVersion " + schemaVersion + " for event type " + type);
        }
    }
}
