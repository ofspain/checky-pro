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
 * Verifies {@link EmailRequestedEvent}'s actual serialization matches
 * {@code contracts/events/auth/email-requested.v1.schema.json} - the structural substitute for
 * code generation (Kimi Phase 3 Finding #6, frozen brief's own disclosed deviation). Mirrors
 * {@code services/auth}'s own {@code EmailRequestedEventPayloadContractTest} exactly.
 */
class EmailRequestedEventContractTest {

    private static final Path SCHEMA_PATH =
            Path.of("../../contracts/events/auth/email-requested.v1.schema.json");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializedEventMatchesTheDocumentedSchema() throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_PATH));

        EmailRequestedEvent event = new EmailRequestedEvent(
                UUID.randomUUID(), "verify_email", "raw-token-value", "owner@example.com",
                Instant.parse("2026-07-13T00:00:00Z"));
        JsonNode serialized = objectMapper.valueToTree(event);

        schema.get("required").forEach(field ->
                assertThat(serialized.has(field.asText()))
                        .as("required field '%s' present", field.asText())
                        .isTrue());

        JsonNode declaredProperties = schema.get("properties");
        serialized.fieldNames().forEachRemaining(field ->
                assertThat(declaredProperties.has(field))
                        .as("serialized field '%s' is declared in the schema (additionalProperties: false)", field)
                        .isTrue());
    }

    @Test
    void bothKnownPurposeValuesDeserializeCleanly() throws IOException {
        for (String purpose : new String[] {"verify_email", "password_reset"}) {
            String json = "{\"accountUuid\":\"" + UUID.randomUUID() + "\",\"purpose\":\"" + purpose
                    + "\",\"token\":\"raw-token\",\"email\":\"owner@example.com\","
                    + "\"occurredAt\":\"2026-07-13T00:00:00Z\"}";
            EmailRequestedEvent event = objectMapper.readValue(json, EmailRequestedEvent.class);
            assertThat(event.purpose()).isEqualTo(purpose);
        }
    }

    /** Kimi Phase 8 Finding #6: field-presence checks alone don't prove the serialized values
     * actually conform to the schema's own {@code format}/{@code pattern} constraints - a
     * lightweight substitute for a full JSON Schema validator (out of this task's own scope, no
     * such dependency exists anywhere in this repo). */
    @Test
    void serializedFieldsConformToTheSchemasFormatConstraints() {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-13T00:00:00Z");
        EmailRequestedEvent event = new EmailRequestedEvent(
                accountUuid, "verify_email", "raw-token-value", "owner@example.com", occurredAt);
        JsonNode serialized = objectMapper.valueToTree(event);

        assertThat(UUID.fromString(serialized.get("accountUuid").asText())).isEqualTo(accountUuid);
        assertThat(serialized.get("email").asText()).contains("@");
        assertThat(Instant.parse(serialized.get("occurredAt").asText())).isEqualTo(occurredAt);
    }

    /** Kimi Phase 3 Finding #4's own concern applied to this DTO too, not just the no-op
     * dispatcher - a future debug/error log accidentally printing this record must not leak the
     * raw token (L4). */
    @Test
    void toStringExcludesTheRawToken() {
        EmailRequestedEvent event = new EmailRequestedEvent(
                UUID.randomUUID(), "verify_email", "super-secret-raw-token", "owner@example.com",
                Instant.parse("2026-07-13T00:00:00Z"));

        assertThat(event.toString()).doesNotContain("super-secret-raw-token");
    }
}
