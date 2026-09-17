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

    /** Phase 11 (Kimi) Gap 7: AC6 explicitly requires byte-for-byte fidelity to {@code design.md}
     * §4c's own JSON text, but nothing before this test enforced it in CI - it was checked once, by
     * hand, via {@code diff} at Phase 6/7. Compares parsed {@link JsonNode} trees rather than raw
     * strings so the check survives incidental whitespace/formatting differences between the
     * markdown-embedded fence and the standalone file while still catching any real content drift
     * (added/removed/changed property, requiredness, enum, or description). */
    @Test
    void schemaIsByteForByteEquivalentToDesignMdSection4c() throws IOException {
        String designMd = Files.readString(Path.of("../../spec/crypto-service/design.md"));
        String marker = "`chain.tx.finalized` (`contracts/events/chain/tx-finalized.v1.schema.json`)";
        int headingIndex = designMd.indexOf(marker);
        assertThat(headingIndex).as("design.md still has the tx-finalized schema heading").isNotEqualTo(-1);

        int fenceStart = designMd.indexOf("```json", headingIndex);
        int fenceContentStart = designMd.indexOf('\n', fenceStart) + 1;
        int fenceEnd = designMd.indexOf("```", fenceContentStart);
        String designMdSnippet = designMd.substring(fenceContentStart, fenceEnd);

        JsonNode expected = objectMapper.readTree(designMdSnippet);
        JsonNode actual = objectMapper.readTree(Files.readString(SCHEMA_PATH));

        assertThat(actual)
                .as("tx-finalized.v1.schema.json must be verbatim-equivalent to design.md §4c (AC6)")
                .isEqualTo(expected);
    }
}
