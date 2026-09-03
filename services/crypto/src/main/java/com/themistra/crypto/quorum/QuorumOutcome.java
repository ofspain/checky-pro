package com.themistra.crypto.quorum;

/**
 * design.md §4c, VERBATIM artifact — copied exactly, not paraphrased.
 *
 * <p>{@code AGREED} — at least 2-of-3 providers matched the fact (L1). {@code HELD} — providers
 * disagreed; ops-alerted; no downstream event; manual resolution only (L2, L3). {@code UNKNOWN_TOKEN}
 * — contract address not on the signed allowlist (L7, R14); this task never produces this value (token
 * allowlist validation is task 11) but the enum's third member exists per the VERBATIM artifact
 * regardless.</p>
 */
public enum QuorumOutcome {
    AGREED,
    HELD,
    UNKNOWN_TOKEN
}
