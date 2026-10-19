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

        // T07: the four consumed payment events, fully populated.
        UUID invoice = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        Instant at = Instant.parse("2026-10-19T00:00:00Z");
        InvoiceCreatedEvent invoiceCreated = new InvoiceCreatedEvent(invoice, UUID.randomUUID(), "ETHEREUM",
                "0xtoken", (short) 6, "1500000", "0xpayto", "order-1", at);
        PaymentSeenEvent paymentSeen = new PaymentSeenEvent(payment, invoice, "ETHEREUM", "0xtx", "0xtoken", 1, at);
        PaymentFinalizedEvent paymentFinalized = new PaymentFinalizedEvent(payment, "ETHEREUM", "0xtx", "0xfrom",
                "0xpayto", "0xtoken", "1500000", 12, false, at);
        ReceiptIssuedEvent receiptIssued = new ReceiptIssuedEvent(invoice, payment, UUID.randomUUID(),
                UUID.randomUUID(), "ETHEREUM", "0xtx", "1500000", "0xtoken", "kms-1", "s3://bucket/key.json", at);
        // The schemas' nullable fields (["string","null"]) also accept an explicit null.
        InvoiceCreatedEvent invoiceCreatedWithoutReference = new InvoiceCreatedEvent(invoice, UUID.randomUUID(),
                "ETHEREUM", "0xtoken", (short) 6, "1500000", "0xpayto", null, at);
        String payments = "../../contracts/events/payments/";
        JsonNode invoiceCreatedSchema = readSchema(payments + "invoice-created.v1.schema.json");
        JsonNode paymentSeenSchema = readSchema(payments + "payment-seen.v1.schema.json");
        JsonNode paymentFinalizedSchema = readSchema(payments + "payment-finalized.v1.schema.json");
        JsonNode receiptIssuedSchema = readSchema(payments + "receipt-issued.v1.schema.json");

        SoftAssertions.assertSoftly(softly -> {
            assertConformsToSchema(softly, objectMapper.valueToTree(emailRequested),
                    emailRequestedSchema, "email-requested.v1.schema.json");
            assertConformsToSchema(softly, objectMapper.valueToTree(userLifecycle),
                    userLifecycleSchema, "user-lifecycle.v1.schema.json");
            assertConformsToSchema(softly, objectMapper.valueToTree(invoiceCreated),
                    invoiceCreatedSchema, "invoice-created.v1.schema.json");
            assertConformsToSchema(softly, objectMapper.valueToTree(invoiceCreatedWithoutReference),
                    invoiceCreatedSchema, "invoice-created.v1.schema.json (null reference)");
            assertConformsToSchema(softly, objectMapper.valueToTree(paymentSeen),
                    paymentSeenSchema, "payment-seen.v1.schema.json");
            assertConformsToSchema(softly, objectMapper.valueToTree(paymentFinalized),
                    paymentFinalizedSchema, "payment-finalized.v1.schema.json");
            assertConformsToSchema(softly, objectMapper.valueToTree(receiptIssued),
                    receiptIssuedSchema, "receipt-issued.v1.schema.json");
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
                JsonNode declaredType = declaredProperties.get(field).get("type");
                softly.assertThat(matchesJsonSchemaType(serialized.get(field), declaredType))
                        .as("serialized field '%s' type matches schema declared type '%s' (schema=%s)",
                                field, declaredType != null && declaredType.isTextual()
                                        ? declaredType.asText() : declaredType, schemaName)
                        .isTrue();
            }
        });
    }

    /** A declared {@code type} is either one JSON Schema type name or, for a nullable field, an
     * array of them such as {@code ["string","null"]}; the node matches when it matches any one.
     * T15 disclosed the array form as unsupported because no auth schema used it; the payments
     * schemas (notification T07) do, so it is supported now. */
    private static boolean matchesJsonSchemaType(JsonNode node, JsonNode declaredType) {
        if (declaredType != null && declaredType.isArray()) {
            for (JsonNode alternative : declaredType) {
                if (matchesJsonSchemaType(node, alternative.asText())) {
                    return true;
                }
            }
            return false;
        }
        return matchesJsonSchemaType(node, declaredType == null ? "" : declaredType.asText());
    }

    private static boolean matchesJsonSchemaType(JsonNode node, String declaredType) {
        return switch (declaredType) {
            case "null" -> node.isNull();
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
        // T07: "null" became a recognised type with the nullable array form; "date" is not a
        // JSON Schema type at all.
        assertThatThrownBy(() -> matchesJsonSchemaType(objectMapper.valueToTree("x"), "date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
    }

    /** T07: the nullable form the payments schemas use, {@code ["string","null"]}. */
    @Test
    void matchesJsonSchemaTypeAcceptsAnyAlternativeOfANullableTypeArray() throws IOException {
        JsonNode nullableString = objectMapper.readTree("[\"string\",\"null\"]");

        assertThat(matchesJsonSchemaType(objectMapper.valueToTree("x"), nullableString)).isTrue();
        assertThat(matchesJsonSchemaType(objectMapper.readTree("null"), nullableString)).isTrue();
        assertThat(matchesJsonSchemaType(objectMapper.valueToTree(1), nullableString)).isFalse();
        assertThat(matchesJsonSchemaType(objectMapper.readTree("null"), objectMapper.readTree("\"string\"")))
                .as("a plain, non-nullable string type still rejects null").isFalse();
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
