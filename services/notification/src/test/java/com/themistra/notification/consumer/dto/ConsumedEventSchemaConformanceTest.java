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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // --- Phase 10: regression guards for the checking mechanism itself, not only its happy path ---
    // Neither real schema today exercises anything but matchesJsonSchemaType's own "string" branch,
    // and shouldConformToConsumedEventSchemas above only ever exercises the fully-conformant case -
    // if either check were silently removed or broken, nothing would fail. These tests close that
    // gap directly, with no real schema file needed for the negative-path proof.

    @Test
    void matchesJsonSchemaTypeAcceptsEveryDeclaredJsonSchemaType() {
        assertThat(matchesJsonSchemaType(objectMapper.valueToTree("x"), "string")).isTrue();
        assertThat(matchesJsonSchemaType(objectMapper.valueToTree(1), "integer")).isTrue();
        assertThat(matchesJsonSchemaType(objectMapper.valueToTree(1.5), "number")).isTrue();
        assertThat(matchesJsonSchemaType(objectMapper.valueToTree(true), "boolean")).isTrue();
        assertThat(matchesJsonSchemaType(objectMapper.valueToTree(List.of(1, 2)), "array")).isTrue();
        assertThat(matchesJsonSchemaType(objectMapper.valueToTree(new ArbitraryObject("x")), "object")).isTrue();
    }

    @Test
    void matchesJsonSchemaTypeRejectsAMismatchedNode() {
        assertThat(matchesJsonSchemaType(objectMapper.valueToTree(1), "string")).isFalse();
        assertThat(matchesJsonSchemaType(objectMapper.valueToTree("x"), "integer")).isFalse();
    }

    @Test
    void matchesJsonSchemaTypeThrowsForAnUnrecognizedDeclaredType() {
        assertThatThrownBy(() -> matchesJsonSchemaType(objectMapper.valueToTree("x"), "null"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }

    /** Proves the mechanism `shouldConformToConsumedEventSchemas` relies on actually catches a real
     * drift in all three ways it claims to (AC1) - a missing required field, an undeclared field,
     * and a type mismatch, all three in one deliberately-broken, synthetic (no real schema file
     * needed) payload/schema pair. Uses a real, non-throwing {@link SoftAssertions} instance
     * directly (not {@code assertSoftly}) so the collected errors can be inspected without the test
     * itself failing. */
    @Test
    void assertConformsToSchemaCatchesAMissingRequiredFieldAnUndeclaredFieldAndATypeMismatch() throws IOException {
        JsonNode schema = objectMapper.readTree(
                "{\"required\":[\"a\",\"b\"],\"properties\":{\"a\":{\"type\":\"string\"},\"b\":{\"type\":\"integer\"}}}");
        JsonNode serialized = objectMapper.readTree("{\"a\":123,\"c\":\"extra\"}");

        SoftAssertions softly = new SoftAssertions();
        assertConformsToSchema(softly, serialized, schema, "synthetic-test-schema.json");

        List<Throwable> errors = softly.errorsCollected();
        assertThat(errors).hasSize(3);
        assertThat(errors).anySatisfy(e -> assertThat(e).hasMessageContaining("required field 'b'"));
        assertThat(errors).anySatisfy(e -> assertThat(e).hasMessageContaining("serialized field 'c'"));
        assertThat(errors).anySatisfy(e -> assertThat(e).hasMessageContaining(
                "serialized field 'a' type matches schema declared type 'string'"));
    }

    /** A trivial, locally-defined record purely so {@code objectMapper.valueToTree} has something
     * that serializes to a genuine JSON object, without depending on either real DTO's own current
     * shape for this unrelated type-check proof. */
    private record ArbitraryObject(String value) {
    }
}
