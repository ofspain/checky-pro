package com.themistra.crypto.watch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies {@link TxLifecyclePublisher.SeenPayload}'s actual serialization matches
 * {@code contracts/events/chain/tx-seen.v1.schema.json} - mirrors {@code
 * UserLifecycleEventPayloadContractTest}'s exact structural-check technique (T23/R28). */
class SeenPayloadContractTest {

    private static final Path SCHEMA_PATH = Path.of("../../contracts/events/chain/tx-seen.v1.schema.json");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializedPayloadMatchesTheDocumentedSchema() throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_PATH));

        TxLifecyclePublisher.SeenPayload payload = new TxLifecyclePublisher.SeenPayload(
                "ETHEREUM:0xtx:seen", UUID.randomUUID(), UUID.randomUUID(), "ETHEREUM", "0xtx",
                "0xtoken", 3, Instant.parse("2026-09-17T00:00:00Z"));
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

        // Phase 11 (Kimi) Gap 1/2: the two assertions above don't, on their own, prove idempotencyKey
        // is actually required or that additionalProperties:false is actually declared - a regression
        // dropping either would still pass them.
        assertThat(schema.get("additionalProperties").asBoolean())
                .as("schema declares additionalProperties: false")
                .isFalse();
        var requiredFields = StreamSupport.stream(schema.get("required").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(requiredFields).as("idempotencyKey is required (AC3)").contains("idempotencyKey");
    }
}
