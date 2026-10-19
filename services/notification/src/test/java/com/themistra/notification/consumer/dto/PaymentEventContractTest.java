package com.themistra.notification.consumer.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Notification T07 (Kimi Phase 3 F2). The four payment DTOs are hand-written, as T06's are; this
 * is their structural substitute for generation. Each record's components must be exactly its
 * schema's properties, and an unknown producer field must not break consumption.
 * {@code ConsumedEventSchemaConformanceTest} checks the serialized types.
 */
class PaymentEventContractTest {

    private static final String SCHEMAS = "../../contracts/events/payments/";
    private static final Map<Class<? extends Record>, String> DTO_SCHEMAS = Map.of(
            InvoiceCreatedEvent.class, "invoice-created.v1.schema.json",
            PaymentSeenEvent.class, "payment-seen.v1.schema.json",
            PaymentFinalizedEvent.class, "payment-finalized.v1.schema.json",
            ReceiptIssuedEvent.class, "receipt-issued.v1.schema.json");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void eachDtoDeclaresExactlyItsSchemasProperties() throws IOException {
        for (Map.Entry<Class<? extends Record>, String> entry : DTO_SCHEMAS.entrySet()) {
            JsonNode schema = objectMapper.readTree(Files.readString(Path.of(SCHEMAS + entry.getValue())));
            Set<String> schemaProperties = new TreeSet<>();
            schema.get("properties").fieldNames().forEachRemaining(schemaProperties::add);
            Set<String> components = new TreeSet<>();
            for (var component : entry.getKey().getRecordComponents()) {
                components.add(component.getName());
            }
            assertThat(components).as("%s matches %s", entry.getKey().getSimpleName(), entry.getValue())
                    .isEqualTo(schemaProperties);
        }
    }

    @Test
    void eachDtoIgnoresAnUnknownProducerField() throws IOException {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<Class<? extends Record>, String> entry : DTO_SCHEMAS.entrySet()) {
            try {
                objectMapper.readValue("{\"aFieldAddedLater\":\"x\"}", entry.getKey());
            } catch (IOException e) {
                failures.add(entry.getKey().getSimpleName() + ": " + e.getMessage());
            }
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void eventsDeserializeFromProducerShapedJson() throws IOException {
        InvoiceCreatedEvent invoice = objectMapper.readValue(
                "{\"invoiceUuid\":\"11111111-1111-1111-1111-111111111111\","
                        + "\"merchantAccountUuid\":\"22222222-2222-2222-2222-222222222222\",\"chain\":\"ETHEREUM\","
                        + "\"tokenContractAddress\":\"0xt\",\"tokenDecimals\":6,\"amount\":\"1000\","
                        + "\"payToAddress\":\"0xp\",\"reference\":null,\"expiresAt\":\"2026-10-19T01:00:00Z\"}",
                InvoiceCreatedEvent.class);
        assertThat(invoice.tokenDecimals()).isEqualTo((short) 6);
        assertThat(invoice.amount()).as("L6: amount stays a decimal string").isEqualTo("1000");
        assertThat(invoice.reference()).isNull();

        ReceiptIssuedEvent receipt = objectMapper.readValue(
                "{\"invoiceUuid\":\"11111111-1111-1111-1111-111111111111\","
                        + "\"paymentUuid\":\"33333333-3333-3333-3333-333333333333\","
                        + "\"receiptUuid\":\"44444444-4444-4444-4444-444444444444\",\"chain\":\"ETHEREUM\","
                        + "\"txHash\":\"0xtx\",\"occurredAt\":\"2026-10-19T00:30:00Z\"}",
                ReceiptIssuedEvent.class);
        assertThat(receipt.merchantAccountUuid()).as("optional in the schema").isNull();
    }
}
