package com.themistra.crypto.adapter.model;

/**
 * Raw per-chain finality state (design.md L4) — deliberately carries data only, never a precomputed
 * finality decision; that judgment belongs solely to each chain's {@code FinalityPolicy} (task 14).
 *
 * <p>{@code finalizedBlockNumber} unifies two chain-specific concepts that turn out to be
 * structurally identical: Ethereum's beacon-chain {@code finalized} checkpoint block number, and
 * Tron's solidified block number. Both represent "the highest block number this chain's own
 * consensus considers irreversibly final" — so for either chain, finality reduces to the same check:
 * {@code txBlockNumber <= finalizedBlockNumber}. Each {@code FinalityPolicy} implementation still
 * owns *how* its chain's adapter obtains that number; this type only carries the result.
 * {@code currentBlockNumber} (the chain's head) is retained as informative context, not required by
 * the finality check itself.</p>
 */
public record FinalityStatus(
        long txBlockNumber,
        long currentBlockNumber,
        long finalizedBlockNumber
) {
}
