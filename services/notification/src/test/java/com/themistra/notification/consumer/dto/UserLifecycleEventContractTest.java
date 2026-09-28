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
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link UserLifecycleEvent}'s actual serialization matches
 * {@code contracts/events/auth/user-lifecycle.v1.schema.json} - the structural substitute for code
 * generation (Kimi Phase 3 Finding #6). Mirrors {@code services/auth}'s own
 * {@code UserLifecycleEventPayloadContractTest} exactly.
 */
class UserLifecycleEventContractTest {

    private static final Path SCHEMA_PATH =
            Path.of("../../contracts/events/auth/user-lifecycle.v1.schema.json");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializedEventMatchesTheDocumentedSchema() throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_PATH));

        UserLifecycleEvent event = new UserLifecycleEvent(
                UUID.randomUUID(), "ACTIVE", "owner@example.com", "user.registered",
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

        var allowedStatuses = StreamSupport.stream(
                        declaredProperties.get("status").get("enum").spliterator(), false)
                .map(JsonNode::asText)
                .toList();
        assertThat(allowedStatuses).contains(serialized.get("status").asText());
    }

    /** The schema deliberately leaves {@code eventType} as an open string, not a closed enum (its
     * own description explains why) - documents that every value known today deserializes cleanly. */
    @Test
    void everyKnownEventTypeValueDeserializesCleanly() throws IOException {
        for (String eventType : new String[] {
                "user.registered", "user.suspended", "user.reinstated",
                "user.deleted", "user.locked", "user.unlocked"}) {
            String json = "{\"accountUuid\":\"" + UUID.randomUUID() + "\",\"status\":\"ACTIVE\","
                    + "\"email\":\"owner@example.com\",\"eventType\":\"" + eventType + "\","
                    + "\"occurredAt\":\"2026-07-13T00:00:00Z\"}";
            UserLifecycleEvent event = objectMapper.readValue(json, UserLifecycleEvent.class);
            assertThat(event.eventType()).isEqualTo(eventType);
        }
    }

    @Test
    void everyStatusValueInTheSchemaEnumDeserializesCleanly() throws IOException {
        JsonNode schema = objectMapper.readTree(Files.readString(SCHEMA_PATH));
        var allowedStatuses = StreamSupport.stream(
                        schema.get("properties").get("status").get("enum").spliterator(), false)
                .map(JsonNode::asText)
                .toList();

        for (String status : allowedStatuses) {
            String json = "{\"accountUuid\":\"" + UUID.randomUUID() + "\",\"status\":\"" + status
                    + "\",\"email\":\"owner@example.com\",\"eventType\":\"user.registered\","
                    + "\"occurredAt\":\"2026-07-13T00:00:00Z\"}";
            UserLifecycleEvent event = objectMapper.readValue(json, UserLifecycleEvent.class);
            assertThat(event.status()).isEqualTo(status);
        }
    }
}
