package com.themistra.auth.account.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.themistra.auth.account.AccountStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies UserLifecycleEventPayload's actual serialization matches
 * contracts/events/auth/user-lifecycle.v1.schema.json — a structural check (required fields
 * present, no undeclared fields, valid enum values) via plain Jackson, rather than adding a
 * JSON-Schema-validation library for the sake of a single contract file (target-design §17.5).
 */
class UserLifecycleEventPayloadContractTest {

    // Maven/Surefire's long-standing convention runs tests with the module directory as the
    // working directory, so this resolves to the monorepo-root contracts/ directory.
    private static final Path SCHEMA_PATH =
            Path.of("../../contracts/events/auth/user-lifecycle.v1.schema.json");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializedPayloadMatchesTheDocumentedSchema() throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_PATH));

        UserLifecycleEventPayload payload = new UserLifecycleEventPayload(
                UUID.randomUUID(), AccountStatus.ACTIVE, Instant.parse("2026-07-13T00:00:00Z"));
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

        var allowedStatuses = StreamSupport.stream(
                        declaredProperties.get("status").get("enum").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(allowedStatuses).contains(serialized.get("status").asText());
    }

    @Test
    void everyAccountStatusValueIsCoveredByTheSchemaEnum() throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_PATH));
        var allowedStatuses = StreamSupport.stream(
                        schema.get("properties").get("status").get("enum").spliterator(), false)
                .map(JsonNode::asText)
                .toList();

        for (AccountStatus status : AccountStatus.values()) {
            assertThat(allowedStatuses)
                    .as("schema enum covers AccountStatus.%s", status)
                    .contains(status.name());
        }
    }
}
