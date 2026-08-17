package com.themistra.auth.account.event;

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
 * Verifies EmailRequestedEventPayload's actual serialization matches
 * contracts/events/auth/email-requested.v1.schema.json — same structural-check approach as
 * {@link UserLifecycleEventPayloadContractTest} (target-design §17.5).
 */
class EmailRequestedEventPayloadContractTest {

    private static final Path SCHEMA_PATH =
            Path.of("../../contracts/events/auth/email-requested.v1.schema.json");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializedPayloadMatchesTheDocumentedSchema() throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_PATH));

        EmailRequestedEventPayload payload = new EmailRequestedEventPayload(
                UUID.randomUUID(), "verify_email", "raw-token-value",
                Instant.parse("2026-07-13T00:00:00Z"));
        JsonNode serialized = objectMapper.valueToTree(payload);

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

    /**
     * The schema deliberately leaves {@code purpose} as an open string, not a closed enum (its own
     * description explains why), so there is no schema-side enum to check against — this test
     * instead documents that both values known today serialize cleanly, matching the payload's own
     * Javadoc ("verify_email", later "password_reset").
     */
    @Test
    void bothKnownPurposeValuesSerializeCleanly() {
        for (String purpose : new String[] {"verify_email", "password_reset"}) {
            EmailRequestedEventPayload payload = new EmailRequestedEventPayload(
                    UUID.randomUUID(), purpose, "raw-token-value", Instant.parse("2026-07-13T00:00:00Z"));
            JsonNode serialized = objectMapper.valueToTree(payload);
            assertThat(serialized.get("purpose").asText()).isEqualTo(purpose);
        }
    }
}
