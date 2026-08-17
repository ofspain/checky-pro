package com.themistra.auth.audit;

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

/**
 * Verifies AuditMirrorPayload's actual serialization matches
 * contracts/events/auth/security-audit.v1.schema.json — same structural-check approach as
 * {@code UserLifecycleEventPayloadContractTest} (target-design §17.5).
 */
class AuditMirrorPayloadContractTest {

    private static final Path SCHEMA_PATH =
            Path.of("../../contracts/events/auth/security-audit.v1.schema.json");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializedPayloadMatchesTheDocumentedSchema() throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_PATH));

        AuditMirrorPayload payload = new AuditMirrorPayload(
                "login.failed", AuditOutcome.FAILURE, UUID.randomUUID(), UUID.randomUUID(),
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

        var allowedOutcomes = StreamSupport.stream(
                        declaredProperties.get("outcome").get("enum").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(allowedOutcomes).contains(serialized.get("outcome").asText());
    }

    /**
     * accountUuid and actorUuid are genuinely nullable (Phase 1: confirmed via
     * AuditService.partitionKey's own null-fallback and the account_uuid/actor_uuid columns'
     * unconstrained schema) — this proves the schema's required list doesn't wrongly demand them.
     */
    @Test
    void payloadWithNullAccountAndActorUuidStillMatchesTheSchema() throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_PATH));

        AuditMirrorPayload payload = new AuditMirrorPayload(
                "system.startup", AuditOutcome.SUCCESS, null, null, Instant.parse("2026-07-13T00:00:00Z"));
        JsonNode serialized = objectMapper.valueToTree(payload);

        schema.get("required").forEach(field ->
                assertThat(serialized.has(field.asText()))
                        .as("required field '%s' present even with null accountUuid/actorUuid", field.asText())
                        .isTrue());
    }

    @Test
    void everyAuditOutcomeValueIsCoveredByTheSchemaEnum() throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_PATH));
        var allowedOutcomes = StreamSupport.stream(
                        schema.get("properties").get("outcome").get("enum").spliterator(), false)
                .map(JsonNode::asText)
                .toList();

        for (AuditOutcome outcome : AuditOutcome.values()) {
            assertThat(allowedOutcomes)
                    .as("schema enum covers AuditOutcome.%s", outcome)
                    .contains(outcome.name());
        }
    }
}
