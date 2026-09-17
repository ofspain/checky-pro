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

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies {@link TxLifecyclePublisher.FinalizedPayload}'s actual serialization matches
 * {@code contracts/events/chain/tx-finalized.v1.schema.json} (T23/R28). Uses a fully-populated,
 * non-null instance (amount/fromAddress/toAddress) - proving the schema's *optional* fields are
 * correctly named too, not just the required ones. */
class FinalizedPayloadContractTest {

    private static final Path SCHEMA_PATH = Path.of("../../contracts/events/chain/tx-finalized.v1.schema.json");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializedPayloadMatchesTheDocumentedSchema() throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_PATH));

        TxLifecyclePublisher.FinalizedPayload payload = new TxLifecyclePublisher.FinalizedPayload(
                "ETHEREUM:0xtx:finalized", UUID.randomUUID(), UUID.randomUUID(), "ETHEREUM", "0xtx",
                "0xtoken", "1000000", "0xfrom", "0xto", Instant.parse("2026-09-17T00:00:00Z"));
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
}
