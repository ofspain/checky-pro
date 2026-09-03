package com.themistra.crypto.adapter.model;

import java.math.BigDecimal;

/**
 * One provider's answer to "what happened with this transaction" — the existence/amount/token/
 * confirmations facts (`V1__chain_baseline.sql`'s {@code observations.fact_type} values, minus
 * {@code finality} which {@link FinalityStatus} covers separately). Quorum (task 9) compares
 * instances of this record returned by independent {@code ChainAdapter}s for the same {@code txHash}.
 *
 * <p>{@code amount} is base units, {@link BigDecimal}, never floating point (agents.md). This type
 * is an in-process value object only — it is never itself JSON-serialized in this service's design:
 * the observation log persists each provider's *raw* response verbatim (L3), never a normalized
 * {@code TxResult}. A future task that does put this type on a JSON boundary must add a decimal-
 * string wire-format guard for {@code amount} at that point (agents.md: decimal strings, never JSON
 * numbers).</p>
 */
public record TxResult(
        boolean exists,
        String txHash,
        String fromAddress,
        String toAddress,
        String tokenContractAddress,
        BigDecimal amount,
        int confirmations,
        long blockNumber
) {
}
