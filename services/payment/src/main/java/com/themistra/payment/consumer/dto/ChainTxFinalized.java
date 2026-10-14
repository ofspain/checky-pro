package com.themistra.payment.consumer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Deserialized shape of {@code chain.tx.finalized} (R10) — field names match
 * {@code contracts/events/chain/tx-finalized.v1.schema.json} exactly (camelCase). The only one
 * of this task's own 3 consumed events that carries {@code amount}/{@code fromAddress}/
 * {@code toAddress}/{@code addressPoisoningFlag} — {@code chain.tx.seen}/{@code chain.tx.confirmed}
 * carry none of these (Phase 0 Finding C). {@code amount} deserializes as a {@code BigDecimal}
 * from the schema's own decimal-string representation (L6) — Spring Boot's autoconfigured
 * {@code ObjectMapper} maps a JSON string field onto a {@code BigDecimal}-typed record component
 * via its registered {@code NumberDeserializers}, no custom deserializer needed (same mechanism
 * already relied on implicitly wherever this codebase deserializes a decimal-string amount).
 */
public record ChainTxFinalized(
        String idempotencyKey,
        UUID watchId,
        UUID invoiceUuid,
        String chain,
        String txHash,
        String fromAddress,
        String toAddress,
        String tokenContractAddress,
        BigDecimal amount,
        int confirmations,
        boolean addressPoisoningFlag,
        Instant occurredAt
) {
}
