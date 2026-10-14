package com.themistra.payment.invoice.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11 Gap 7: {@code InvoiceServiceTest} only verifies the in-memory {@code
 * InvoiceCreatedPayload} object's fields via a mocked {@code OutboxPublisher} - it never
 * exercises real Jackson serialization. This proves the actual wire shape sibling consumers will
 * deserialize: {@code amount} is a textual decimal (L6), and {@code expiresAt} serializes as an
 * ISO-8601 string, not a numeric epoch value - the same empirical check (real, Boot-autoconfigured
 * {@code ObjectMapper}, not a bare {@code new ObjectMapper()}) that disproved Kimi's Phase 8
 * finding 3 for {@code RegisterWatchRequest}, applied here to the outbox event side too.
 */
class InvoiceCreatedPayloadSerializationTest {

    @Test
    void amountSerializesAsTextualDecimalAndExpiresAtAsIso8601String() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(JacksonAutoConfiguration.class);
            context.refresh();
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

            InvoiceCreatedPayload payload = new InvoiceCreatedPayload(
                    UUID.randomUUID(), UUID.randomUUID(), "ETHEREUM", "0xabc", (short) 18,
                    "1000", "0xdef", "order-1", Instant.parse("2026-07-13T01:00:00Z"));

            String json = objectMapper.writeValueAsString(payload);
            JsonNode node = objectMapper.readTree(json);

            assertThat(node.get("amount").isTextual()).as("amount must be a JSON string, never a number").isTrue();
            assertThat(node.get("amount").asText()).isEqualTo("1000");
            assertThat(node.get("expiresAt").isTextual())
                    .as("expiresAt must serialize as an ISO-8601 string, never a numeric timestamp")
                    .isTrue();
            assertThat(node.get("expiresAt").asText()).isEqualTo("2026-07-13T01:00:00Z");
        }
    }
}
