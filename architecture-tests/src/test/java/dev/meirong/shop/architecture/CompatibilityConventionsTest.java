package dev.meirong.shop.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.meirong.shop.contracts.event.EventEnvelope;
import dev.meirong.shop.contracts.event.BuyerRegisteredEventData;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CompatibilityConventionsTest {

    private static final String API_PACKAGE = "dev.meirong.shop.contracts.api";
    private static final String EVENT_PACKAGE = "dev.meirong.shop.contracts.event";

    @Test
    void apiContracts_useVersionedBasePaths() throws Exception {
        List<String> violations = new ArrayList<>();

        for (Class<?> apiType : importTopLevelClasses(API_PACKAGE)) {
            if (!apiType.getSimpleName().endsWith("Api")) {
                continue;
            }
            Field basePath = resolveBasePathField(apiType);
            String value = (String) basePath.get(null);
            if (!value.matches("^/.+/(v\\d+|internal)$")) {
                violations.add(apiType.getName()
                        + " BASE_PATH must end with /v<version> for external APIs or /internal for trusted internal APIs, but was "
                        + value);
            }
        }

        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void eventContracts_ignoreUnknownFields_andDefaultLegacyMetadata() throws Exception {
        List<String> missingAnnotation = importTopLevelClasses(EVENT_PACKAGE).stream()
                .filter(type -> !type.isAnnotationPresent(JsonIgnoreProperties.class))
                .map(Class::getName)
                .sorted()
                .toList();

        assertTrue(missingAnnotation.isEmpty(),
                () -> "Missing @JsonIgnoreProperties(ignoreUnknown = true): "
                        + String.join(", ", missingAnnotation));

        EventEnvelope<BuyerRegisteredEventData> envelope = new EventEnvelope<>(
                "evt-1",
                "auth-server",
                "USER_REGISTERED",
                Instant.parse("2026-03-24T00:00:00Z"),
                new BuyerRegisteredEventData("buyer-1", "demo", "demo@example.com"));

        assertEquals(EventEnvelope.CURRENT_SCHEMA_VERSION, envelope.schemaVersion());
        assertEquals(EventEnvelope.JSON_CONTENT_TYPE, envelope.contentType());

        EventEnvelope<BuyerRegisteredEventData> legacyEnvelope = JsonMapper.builder()
                .findAndAddModules()
                .build()
                .readValue(
                        """
                        {
                          "eventId": "evt-legacy",
                          "source": "auth-server",
                          "type": "USER_REGISTERED",
                          "timestamp": "2026-03-24T00:00:00Z",
                          "data": {
                            "playerId": "buyer-2",
                            "username": "legacy",
                            "email": "legacy@example.com",
                            "futureField": "ignored"
                          },
                          "futureRootField": "ignored"
                        }
                        """,
                        new TypeReference<>() {});

        assertEquals(EventEnvelope.CURRENT_SCHEMA_VERSION, legacyEnvelope.schemaVersion());
        assertEquals(EventEnvelope.JSON_CONTENT_TYPE, legacyEnvelope.contentType());
        legacyEnvelope.assertSupportedSchema(EventEnvelope.CURRENT_SCHEMA_VERSION);
    }

    private List<Class<?>> importTopLevelClasses(String packageName) {
        return new ClassFileImporter()
                .importPackages(packageName)
                .stream()
                .filter(javaClass -> javaClass.getPackageName().equals(packageName))
                .filter(javaClass -> !javaClass.getName().contains("$"))
                .map(javaClass -> {
                    try {
                        return Class.forName(javaClass.getName());
                    } catch (ClassNotFoundException exception) {
                        throw new IllegalStateException("Failed to load " + javaClass.getName(), exception);
                    }
                })
                .sorted(Comparator.comparing(Class::getName))
                .collect(Collectors.toList());
    }

    private Field resolveBasePathField(Class<?> apiType) {
        try {
            return apiType.getDeclaredField("BASE_PATH");
        } catch (NoSuchFieldException ignored) {
            try {
                return apiType.getDeclaredField("BASE");
            } catch (NoSuchFieldException exception) {
                throw new IllegalStateException(apiType.getName() + " must declare BASE_PATH or BASE", exception);
            }
        }
    }
}
