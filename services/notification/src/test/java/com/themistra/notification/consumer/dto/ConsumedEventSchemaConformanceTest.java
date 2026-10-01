package com.themistra.notification.consumer.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T15 (R19) - the literal {@code package.md} §8 named test. The substance R19 describes was
 * already thoroughly covered at T06 by {@link EmailRequestedEventContractTest} and
 * {@link UserLifecycleEventContractTest}, each already mirroring {@code services/auth}'s own
 * producer-side contract-test pattern exactly; this class exists only to give that already-covered
 * substance one single, literally-named regression guard, alongside - not instead of - the existing,
 * more detailed coverage (format constraints, enum/purpose coverage, token-redaction stay there,
 * not duplicated here).
 *
 * <p>Every assertion operates on {@code JsonNode} field names/types only, never the raw serialized
 * JSON string or any field's own value (L4) - a failure message can therefore never embed
 * {@link EmailRequestedEvent}'s own raw {@code token}.</p>
 */
class ConsumedEventSchemaConformanceTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldConformToConsumedEventSchemas() throws IOException {
        EmailRequestedEvent emailRequested = new EmailRequestedEvent(
                UUID.randomUUID(), "verify_email", "redacted-test-token", "owner@example.com",
                Instant.parse("2026-07-13T00:00:00Z"));
        assertConformsToSchema(objectMapper.valueToTree(emailRequested),
                Path.of("../../contracts/events/auth/email-requested.v1.schema.json"));

        UserLifecycleEvent userLifecycle = new UserLifecycleEvent(
                UUID.randomUUID(), "ACTIVE", "owner@example.com", "user.registered",
                Instant.parse("2026-07-13T00:00:00Z"));
        assertConformsToSchema(objectMapper.valueToTree(userLifecycle),
                Path.of("../../contracts/events/auth/user-lifecycle.v1.schema.json"));
    }

    private void assertConformsToSchema(JsonNode serialized, Path schemaPath) throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(schemaPath));
        JsonNode declaredProperties = schema.get("properties");
        String schemaName = schemaPath.getFileName().toString();

        schema.get("required").forEach(field ->
                assertThat(serialized.has(field.asText()))
                        .as("required field '%s' present (schema=%s)", field.asText(), schemaName)
                        .isTrue());

        serialized.fieldNames().forEachRemaining(field ->
                assertThat(declaredProperties.has(field))
                        .as("serialized field '%s' is declared in the schema (schema=%s)", field, schemaName)
                        .isTrue());

        declaredProperties.fieldNames().forEachRemaining(field -> {
            if (serialized.has(field)) {
                String declaredType = declaredProperties.get(field).get("type").asText();
                assertThat(matchesJsonSchemaType(serialized.get(field), declaredType))
                        .as("serialized field '%s' type matches schema declared type '%s' (schema=%s)",
                                field, declaredType, schemaName)
                        .isTrue();
            }
        });
    }

    private static boolean matchesJsonSchemaType(JsonNode node, String declaredType) {
        return switch (declaredType) {
            case "string" -> node.isTextual();
            case "integer" -> node.isIntegralNumber();
            case "number" -> node.isNumber();
            case "boolean" -> node.isBoolean();
            case "object" -> node.isObject();
            case "array" -> node.isArray();
            default -> throw new IllegalArgumentException("unsupported JSON Schema type: " + declaredType);
        };
    }
}
