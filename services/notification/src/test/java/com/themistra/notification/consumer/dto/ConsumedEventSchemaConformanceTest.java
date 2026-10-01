package com.themistra.notification.consumer.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

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

    /** Phase 8 Finding #2 (self-review Finding #2): both schema checks run and report
     * independently via {@link SoftAssertions} - without it, a failure in the first DTO's own
     * conformance check would throw immediately and mask any simultaneous drift in the second,
     * unlike the two existing, independent per-DTO contract test classes this one sits alongside.
     * Each schema file is read up front, before entering the soft-assertion block, so neither
     * call inside it needs to declare a checked {@code IOException}. */
    @Test
    void shouldConformToConsumedEventSchemas() throws IOException {
        EmailRequestedEvent emailRequested = new EmailRequestedEvent(
                UUID.randomUUID(), "verify_email", "redacted-test-token", "owner@example.com",
                Instant.parse("2026-07-13T00:00:00Z"));
        JsonNode emailRequestedSchema = readSchema("../../contracts/events/auth/email-requested.v1.schema.json");

        UserLifecycleEvent userLifecycle = new UserLifecycleEvent(
                UUID.randomUUID(), "ACTIVE", "owner@example.com", "user.registered",
                Instant.parse("2026-07-13T00:00:00Z"));
        JsonNode userLifecycleSchema = readSchema("../../contracts/events/auth/user-lifecycle.v1.schema.json");

        SoftAssertions.assertSoftly(softly -> {
            assertConformsToSchema(softly, objectMapper.valueToTree(emailRequested),
                    emailRequestedSchema, "email-requested.v1.schema.json");
            assertConformsToSchema(softly, objectMapper.valueToTree(userLifecycle),
                    userLifecycleSchema, "user-lifecycle.v1.schema.json");
        });
    }

    private JsonNode readSchema(String path) throws IOException {
        return objectMapper.readTree(Files.readString(Path.of(path)));
    }

    private void assertConformsToSchema(SoftAssertions softly, JsonNode serialized, JsonNode schema,
                                         String schemaName) {
        JsonNode declaredProperties = schema.get("properties");

        schema.get("required").forEach(field ->
                softly.assertThat(serialized.has(field.asText()))
                        .as("required field '%s' present (schema=%s)", field.asText(), schemaName)
                        .isTrue());

        serialized.fieldNames().forEachRemaining(field ->
                softly.assertThat(declaredProperties.has(field))
                        .as("serialized field '%s' is declared in the schema (schema=%s)", field, schemaName)
                        .isTrue());

        declaredProperties.fieldNames().forEachRemaining(field -> {
            if (serialized.has(field)) {
                String declaredType = declaredProperties.get(field).get("type").asText();
                softly.assertThat(matchesJsonSchemaType(serialized.get(field), declaredType))
                        .as("serialized field '%s' type matches schema declared type '%s' (schema=%s)",
                                field, declaredType, schemaName)
                        .isTrue();
            }
        });
    }

    /** Phase 8 Finding #1 (self-review Finding #1, disclosed, not fixed): assumes {@code type} is
     * always a single JSON string, matching every real schema under {@code contracts/events/auth/}
     * today (confirmed directly - none uses the also-legal JSON Schema array form, e.g.
     * {@code ["string","null"]}, for a nullable field). If a future schema revision ever used that
     * form, {@code declaredType} would arrive here as {@code ""} ({@code JsonNode.asText()}'s own
     * default for a non-scalar node) and fall into the {@code default} branch below, failing with a
     * confusing message rather than correctly validating against either allowed type - not fixed
     * speculatively for a case no real schema uses today. */
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
