package com.themistra.crypto.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies {@link ProviderDegradedPublisher.Payload}'s actual serialization matches
 * {@code contracts/events/chain/provider-degraded.v1.schema.json} (T23/R28) - a genuinely different
 * envelope from the four {@code chain.tx.*} events (no {@code idempotencyKey}/{@code watchId}/
 * {@code invoiceUuid} field; that key is passed to {@code OutboxPublisher} separately, see the
 * schema's own {@code description}). */
class ProviderDegradedPayloadContractTest {

    private static final Path SCHEMA_PATH = Path.of("../../contracts/events/chain/provider-degraded.v1.schema.json");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializedPayloadMatchesTheDocumentedSchema() throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_PATH));

        ProviderDegradedPublisher.Payload payload = new ProviderDegradedPublisher.Payload(
                "ETHEREUM", "alchemy", DegradationReason.UNHEALTHY, Instant.parse("2026-09-17T00:00:00Z"));
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

    /** Frozen brief AC5: {@link DegradationReason}'s real Java enum must not silently fall behind this
     * schema's own declared {@code reason} enum - mirrors {@code
     * UserLifecycleEventPayloadContractTest.everyAccountStatusValueIsCoveredByTheSchemaEnum}'s
     * identical technique. */
    @Test
    void everyDegradationReasonValueIsCoveredByTheSchemaEnum() throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_PATH));
        var allowedReasons = StreamSupport.stream(
                        schema.get("properties").get("reason").get("enum").spliterator(), false)
                .map(JsonNode::asText)
                .toList();

        for (DegradationReason reason : DegradationReason.values()) {
            assertThat(allowedReasons)
                    .as("schema enum covers DegradationReason.%s", reason)
                    .contains(reason.name());
        }
    }
}
