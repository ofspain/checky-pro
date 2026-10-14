package com.themistra.payment.consumer.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3/4 Finding 7: field names match {@code contracts/events/chain/
 * {tx-seen,tx-confirmed,tx-finalized}.v1.schema.json} exactly (camelCase) - each inline JSON
 * literal below mirrors the real schema's own required+optional field set field-for-field, cited
 * in each test's own comment rather than a full JSON-Schema-validation harness (no such
 * infrastructure exists yet in this codebase - task 24's own separate scope).
 */
class ChainEventDtoDeserializationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    /** Mirrors {@code contracts/events/chain/tx-seen.v1.schema.json}'s own required list exactly:
     * idempotencyKey, watchId, chain, txHash, tokenContractAddress, confirmations, occurredAt
     * (invoiceUuid is present but not required - included here anyway, since a real producer is
     * expected to send it). */
    @Test
    void shouldDeserializeChainTxSeen() throws Exception {
        UUID watchId = UUID.randomUUID();
        UUID invoiceUuid = UUID.randomUUID();
        String json = """
                {"idempotencyKey":"ETHEREUM:0xabc:chain.tx.seen","watchId":"%s","invoiceUuid":"%s",
                 "chain":"ETHEREUM","txHash":"0xabc","tokenContractAddress":"0x111",
                 "confirmations":1,"occurredAt":"2026-07-13T00:00:00Z"}
                """.formatted(watchId, invoiceUuid);

        ChainTxSeen event = OBJECT_MAPPER.readValue(json, ChainTxSeen.class);

        assertThat(event.idempotencyKey()).isEqualTo("ETHEREUM:0xabc:chain.tx.seen");
        assertThat(event.watchId()).isEqualTo(watchId);
        assertThat(event.invoiceUuid()).isEqualTo(invoiceUuid);
        assertThat(event.chain()).isEqualTo("ETHEREUM");
        assertThat(event.txHash()).isEqualTo("0xabc");
        assertThat(event.tokenContractAddress()).isEqualTo("0x111");
        assertThat(event.confirmations()).isEqualTo(1);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-07-13T00:00:00Z"));
    }

    /** Mirrors {@code contracts/events/chain/tx-confirmed.v1.schema.json}'s own required list -
     * identical field set to {@code tx-seen} (a different {@code idempotencyKey} suffix only). */
    @Test
    void shouldDeserializeChainTxConfirmed() throws Exception {
        UUID watchId = UUID.randomUUID();
        String json = """
                {"idempotencyKey":"ETHEREUM:0xabc:chain.tx.confirmed","watchId":"%s",
                 "invoiceUuid":"%s","chain":"ETHEREUM","txHash":"0xabc",
                 "tokenContractAddress":"0x111","confirmations":3,
                 "occurredAt":"2026-07-13T00:05:00Z"}
                """.formatted(watchId, UUID.randomUUID());

        ChainTxConfirmed event = OBJECT_MAPPER.readValue(json, ChainTxConfirmed.class);

        assertThat(event.watchId()).isEqualTo(watchId);
        assertThat(event.confirmations()).isEqualTo(3);
    }

    /** Mirrors {@code contracts/events/chain/tx-finalized.v1.schema.json}'s own required list -
     * the only one of the 3 consumed events carrying {@code fromAddress}/{@code toAddress}/
     * {@code amount}/{@code addressPoisoningFlag} (Phase 0 Finding C). {@code amount} deserializes
     * from a JSON string to {@code BigDecimal} with full precision preserved - confirmed
     * empirically (Phase 6), not assumed. */
    @Test
    void shouldDeserializeChainTxFinalizedWithFullPrecisionAmount() throws Exception {
        UUID watchId = UUID.randomUUID();
        String json = """
                {"idempotencyKey":"ETHEREUM:0xabc:chain.tx.finalized","watchId":"%s",
                 "invoiceUuid":"%s","chain":"ETHEREUM","txHash":"0xabc",
                 "fromAddress":"0xfrom","toAddress":"0xto","tokenContractAddress":"0x111",
                 "amount":"123456789012345678901234567890","confirmations":12,
                 "addressPoisoningFlag":true,"occurredAt":"2026-07-13T00:10:00Z"}
                """.formatted(watchId, UUID.randomUUID());

        ChainTxFinalized event = OBJECT_MAPPER.readValue(json, ChainTxFinalized.class);

        assertThat(event.fromAddress()).isEqualTo("0xfrom");
        assertThat(event.toAddress()).isEqualTo("0xto");
        assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("123456789012345678901234567890"));
        assertThat(event.confirmations()).isEqualTo(12);
        assertThat(event.addressPoisoningFlag()).isTrue();
    }

    /** T12. Mirrors {@code contracts/events/chain/tx-reorged.v1.schema.json}'s own required
     * list - unlike the other three, it carries no {@code confirmations} field at all. */
    @Test
    void shouldDeserializeChainTxReorged() throws Exception {
        UUID watchId = UUID.randomUUID();
        String json = """
                {"idempotencyKey":"ETHEREUM:0xabc:chain.tx.reorged","watchId":"%s",
                 "invoiceUuid":"%s","chain":"ETHEREUM","txHash":"0xabc",
                 "tokenContractAddress":"0x111","occurredAt":"2026-07-13T00:15:00Z"}
                """.formatted(watchId, UUID.randomUUID());

        ChainTxReorged event = OBJECT_MAPPER.readValue(json, ChainTxReorged.class);

        assertThat(event.idempotencyKey()).isEqualTo("ETHEREUM:0xabc:chain.tx.reorged");
        assertThat(event.watchId()).isEqualTo(watchId);
        assertThat(event.chain()).isEqualTo("ETHEREUM");
        assertThat(event.txHash()).isEqualTo("0xabc");
        assertThat(event.tokenContractAddress()).isEqualTo("0x111");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-07-13T00:15:00Z"));
    }

    /** Phase 11 Gap 5 (Kimi), part (b): the real schemas declare {@code additionalProperties:
     * false}. Confirmed empirically (Phase 11, not assumed): Jackson's own
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} defaults to {@code true} with no extra configuration, so
     * a producer sending an extra field the schema doesn't define fails loudly rather than being
     * silently accepted. Part (a) of Kimi's own suggestion (a missing required field should
     * throw) is deliberately NOT added - these are plain Java records with no {@code
     * @JsonProperty(required = true)} annotations, so a missing field currently deserializes to
     * {@code null} (or the primitive default), not a thrown exception; adding that validation now
     * would be new behavior beyond this task's own frozen brief, not a test of existing behavior. */
    @Test
    void shouldRejectAnUnrecognizedPropertyNotInTheRealSchema() {
        String jsonWithExtraField = """
                {"idempotencyKey":"ETHEREUM:0xabc:chain.tx.seen","watchId":"%s","invoiceUuid":"%s",
                 "chain":"ETHEREUM","txHash":"0xabc","tokenContractAddress":"0x111",
                 "confirmations":1,"occurredAt":"2026-07-13T00:00:00Z","notARealField":"x"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> OBJECT_MAPPER.readValue(jsonWithExtraField, ChainTxSeen.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }
}
