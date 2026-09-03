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
 *
 * <p><b>Launch-scope shape (Phase 9 Finding).</b> This single-block-number shape fits Ethereum and
 * Tron only — the two launch chains (package.md §2). BASE/ARB ("L2 confirmed AND batch settled on
 * L1") and Solana ("finalized" commitment level) describe multi-fact finality concepts a single
 * {@code long} cannot represent; per package.md §2, the adapter interface "must not preclude" those
 * chains but they are explicitly "not built here." A future task adding either chain family may need
 * a new field or a richer type — not a concern this task's own scope resolves.</p>
 */
public record FinalityStatus(
        long txBlockNumber,
        long currentBlockNumber,
        long finalizedBlockNumber
) {
}
