package com.themistra.payment.consumer.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
}
