package com.themistra.payment.receipt.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11 Gap 9 (Kimi): mirrors {@code InvoiceCreatedPayloadSerializationTest}'s own
 * established pattern (T07) - {@code ReceiptServiceTest} only verifies the in-memory {@code
 * ReceiptIssuedPayload} object's fields via a mocked {@code OutboxPublisher}, never real Jackson
 * serialization. Proves the actual wire shape: {@code amount} is a textual decimal (L6),
 * {@code occurredAt} serializes as an ISO-8601 string (the schema's own {@code "format":
 * "date-time"}), not a numeric epoch value.
 */
class ReceiptIssuedPayloadSerializationTest {

    @Test
    void amountSerializesAsTextualDecimalAndOccurredAtAsIso8601String() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(JacksonAutoConfiguration.class);
            context.refresh();
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

            ReceiptIssuedPayload payload = new ReceiptIssuedPayload(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    "ETHEREUM", "0xabc123", "1000000", "0xdef456", "kms-key-1",
                    "s3://bucket/receipts/uuid.json", Instant.parse("2026-10-15T00:00:01Z"));

            String json = objectMapper.writeValueAsString(payload);
            JsonNode node = objectMapper.readTree(json);

            assertThat(node.get("amount").isTextual()).as("amount must be a JSON string, never a number").isTrue();
            assertThat(node.get("amount").asText()).isEqualTo("1000000");
            assertThat(node.get("occurredAt").isTextual())
                    .as("occurredAt must serialize as an ISO-8601 string, never a numeric timestamp")
                    .isTrue();
            assertThat(node.get("occurredAt").asText()).isEqualTo("2026-10-15T00:00:01Z");
        }
    }
}
