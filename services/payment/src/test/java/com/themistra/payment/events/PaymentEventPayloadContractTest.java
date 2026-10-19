package com.themistra.payment.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.themistra.payment.invoice.event.InvoiceCreatedPayload;
import com.themistra.payment.payment.event.PaymentFinalizedPayload;
import com.themistra.payment.payment.event.PaymentSeenPayload;
import com.themistra.payment.receipt.event.ReceiptIssuedPayload;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * T24 (R31): every payload this service publishes through the outbox matches its schema under
 * {@code contracts/events/payments/}. Mirrors auth's {@code UserLifecycleEventPayloadContractTest}
 * technique (plain Jackson structural comparison, no JSON-Schema library, no Spring context), and
 * goes further in three ways the frozen brief asks for. The schema's property set must equal the
 * record's components in both directions, so a field added to either side fails here. Each Java
 * type must map to its documented type/format, so a monetary {@code amount} documented as a
 * number fails (L6). And {@code additionalProperties: false} plus the real partition key in the
 * description are asserted outright.
 */
class PaymentEventPayloadContractTest {

    // Surefire runs with the module directory as the working directory, so this resolves to the
    // monorepo-root contracts/ directory (same convention as every sibling contract test).
    private static final Path SCHEMA_DIR = Path.of("../../contracts/events/payments");

    private static final Instant NOW = Instant.parse("2026-10-19T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void invoiceCreatedPayloadMatchesItsSchema() throws IOException {
        assertMatches("invoice-created.v1.schema.json", InvoiceCreatedPayload.class,
                Set.of("invoiceUuid", "merchantAccountUuid", "chain", "tokenContractAddress",
                        "tokenDecimals", "amount", "payToAddress", "expiresAt"),
                Set.of("reference"),
                "invoiceUuid",
                new InvoiceCreatedPayload(UUID.randomUUID(), UUID.randomUUID(), "ETHEREUM", "0xtoken",
                        (short) 6, "1000000", "0xpayto", "order-1", NOW),
                new InvoiceCreatedPayload(UUID.randomUUID(), UUID.randomUUID(), "TRON", "Ttoken",
                        (short) 6, "1000000", "Tpayto", null, NOW));
    }

    @Test
    void paymentSeenPayloadMatchesItsSchema() throws IOException {
        assertMatches("payment-seen.v1.schema.json", PaymentSeenPayload.class,
                Set.of("paymentUuid", "invoiceUuid", "chain", "txHash", "tokenContractAddress",
                        "confirmations", "occurredAt"),
                Set.of(),
                "paymentUuid",
                new PaymentSeenPayload(UUID.randomUUID(), UUID.randomUUID(), "ETHEREUM", "0xtx",
                        "0xtoken", 1, NOW));
    }

    @Test
    void paymentFinalizedPayloadMatchesItsSchema() throws IOException {
        assertMatches("payment-finalized.v1.schema.json", PaymentFinalizedPayload.class,
                Set.of("paymentUuid", "chain", "txHash", "tokenContractAddress", "amount",
                        "confirmations", "addressPoisoningFlag", "occurredAt"),
                Set.of("fromAddress", "toAddress"),
                "paymentUuid",
                new PaymentFinalizedPayload(UUID.randomUUID(), "ETHEREUM", "0xtx", "0xfrom", "0xto",
                        "0xtoken", "1000000", 12, false, NOW),
                new PaymentFinalizedPayload(UUID.randomUUID(), "TRON", "Ttx", null, null,
                        "Ttoken", "1000000", 20, true, NOW));
    }

    /** Required set is {@code design.md} §4c's pinned six (Phase 4 G1), not every field. */
    @Test
    void receiptIssuedPayloadMatchesItsSchema() throws IOException {
        assertMatches("receipt-issued.v1.schema.json", ReceiptIssuedPayload.class,
                Set.of("invoiceUuid", "paymentUuid", "receiptUuid", "chain", "txHash", "occurredAt"),
                Set.of(),
                "receiptUuid",
                new ReceiptIssuedPayload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), "ETHEREUM", "0xtx", "1000000", "0xtoken",
                        "arn:aws:kms:eu-west-1:000000000000:key/test", "s3://bucket/key", NOW));
    }

    @Test
    void everySchemaHasTheFrozenIdAndTitle() throws IOException {
        for (String[] entry : new String[][] {
                {"invoice-created.v1.schema.json", "payments.invoice.created"},
                {"payment-seen.v1.schema.json", "payments.payment.seen"},
                {"payment-finalized.v1.schema.json", "payments.payment.finalized"},
                {"receipt-issued.v1.schema.json", "payments.receipt.issued"}}) {
            JsonNode schema = readSchema(entry[0]);
            assertThat(schema.get("$schema").asText()).as("%s $schema", entry[0])
                    .isEqualTo("https://json-schema.org/draft/2020-12/schema");
            assertThat(schema.get("$id").asText()).as("%s $id", entry[0])
                    .isEqualTo("https://checky.pro/contracts/events/payments/" + entry[0]);
            assertThat(schema.get("title").asText()).as("%s title", entry[0])
                    .isEqualTo(entry[1] + " (schema_version 1)");
            assertThat(schema.get("type").asText()).as("%s type", entry[0]).isEqualTo("object");
        }
    }

    private void assertMatches(String file, Class<? extends Record> type, Set<String> expectedRequired,
                               Set<String> expectedNullable, String partitionKey, Object... instances)
            throws IOException {
        JsonNode schema = readSchema(file);
        JsonNode properties = schema.get("properties");

        assertThat(schema.has("additionalProperties")).as("%s declares additionalProperties", file).isTrue();
        assertThat(schema.get("additionalProperties").isBoolean() && !schema.get("additionalProperties").asBoolean())
                .as("%s has additionalProperties: false", file).isTrue();

        Set<String> componentNames = Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(fieldNames(properties)).as("%s properties equal %s's components", file, type.getSimpleName())
                .isEqualTo(componentNames);
        assertThat(textSet(schema.get("required"))).as("%s required set", file).isEqualTo(expectedRequired);

        for (RecordComponent component : type.getRecordComponents()) {
            assertTypeMatches(file, component, properties.get(component.getName()),
                    expectedNullable.contains(component.getName()));
        }

        assertThat(schema.get("description").asText()).as("%s names its real partition key", file)
                .contains("Partition key = " + partitionKey + ".");

        for (Object instance : instances) {
            JsonNode serialized = objectMapper.valueToTree(instance);
            expectedRequired.forEach(field -> assertThat(serialized.hasNonNull(field))
                    .as("%s: required field '%s' present and non-null", file, field).isTrue());
            serialized.fieldNames().forEachRemaining(field -> assertThat(properties.has(field))
                    .as("%s: serialized field '%s' is declared", file, field).isTrue());
            serialized.fields().forEachRemaining(field -> {
                if (field.getValue().isNull()) {
                    assertThat(expectedNullable).as("%s: '%s' serialized as null, so must be nullable",
                            file, field.getKey()).contains(field.getKey());
                }
            });
        }
    }

    /** Java type to documented JSON-Schema type/format. Unknown types fail loudly, so a new
     * field type can never pass unchecked. Nullable fields must be exactly ["string","null"]. */
    private static void assertTypeMatches(String owner, RecordComponent component, JsonNode property,
                                          boolean nullable) {
        String name = component.getName();
        Class<?> javaType = component.getType();
        String expectedType;
        String expectedFormat = null;
        if (javaType == UUID.class) {
            expectedType = "string";
            expectedFormat = "uuid";
        } else if (javaType == Instant.class) {
            expectedType = "string";
            expectedFormat = "date-time";
        } else if (javaType == String.class || javaType == BigDecimal.class) {
            expectedType = "string";
        } else if (javaType == short.class || javaType == int.class) {
            expectedType = "integer";
        } else if (javaType == boolean.class) {
            expectedType = "boolean";
        } else {
            fail("%s.%s: no contract type mapping for Java type %s", owner, name, javaType.getName());
            return;
        }

        JsonNode typeNode = property.get("type");
        if (nullable) {
            assertThat(typeNode.isArray()).as("%s.%s is a nullable type array", owner, name).isTrue();
            assertThat(textSet(typeNode)).as("%s.%s type", owner, name).isEqualTo(Set.of(expectedType, "null"));
        } else {
            assertThat(typeNode.isTextual()).as("%s.%s is a single, non-nullable type", owner, name).isTrue();
            assertThat(typeNode.asText()).as("%s.%s type", owner, name).isEqualTo(expectedType);
        }

        if (expectedFormat == null) {
            assertThat(property.has("format")).as("%s.%s declares no format", owner, name).isFalse();
        } else {
            assertThat(property.path("format").asText()).as("%s.%s format", owner, name).isEqualTo(expectedFormat);
        }
    }

    private JsonNode readSchema(String file) throws IOException {
        return objectMapper.readTree(Files.readString(SCHEMA_DIR.resolve(file)));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Set<String> textSet(JsonNode arrayNode) {
        Set<String> values = new HashSet<>();
        arrayNode.forEach(value -> values.add(value.asText()));
        return values;
    }
}
