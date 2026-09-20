package com.themistra.crypto.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Token identity by {@code <chain, contractAddress>} only, never a symbol (R13, L7).
 * {@link #validate} returns {@link Optional#empty()} for {@code UNKNOWN_TOKEN} (R14) rather than
 * {@code quorum.QuorumOutcome} directly - {@code token/} stays {@code quorum}-agnostic; no caller
 * currently exists to need that mapping (mirrors every prior task's own "no real caller yet" pattern).
 *
 * <p><b>Current-version semantics (Phase 9, Kimi Phase 8 Issues 1 and 3 - corrects the original,
 * global-version design).</b> "Current version" is scoped per chain, not globally: {@link
 * TokenAllowlistRepository#findCurrentVersionEntry} resolves, in one atomic query, the highest {@code
 * version} present for the given chain and matches against exactly that version. A superseded
 * version's rows remain for audit/history but are never active. Per-chain scoping matters because
 * different chains' allowlists have no reason to change in lockstep - a version bump on one chain must
 * never make another, unrelated chain's unchanged entries appear to vanish. The original design used a
 * single global maximum across the whole table, which would have done exactly that; the single-query
 * fix also closes a narrow read-committed race the original two-query version had (a concurrently-
 * committed newer version between the max-read and the keyed lookup). An entirely empty table (or an
 * empty chain) means every lookup is {@code UNKNOWN_TOKEN} - a fail-loud, not fail-open, default.</p>
 *
 * <p><b>Fail-fast on an unrecognized {@code chain} (Phase 3 Kimi Issue 5).</b> A typo or unsupported
 * chain throws {@link IllegalArgumentException} rather than silently returning {@code UNKNOWN_TOKEN} -
 * conflating a caller bug with a genuine non-allowlisted-token signal would undermine R14's own
 * "surfaced loudly" intent.</p>
 *
 * <p><b>Exact string matching only.</b> No case-folding, no chain-aware address normalization -
 * EIP-55 checksum handling (Ethereum) and Base58Check validation (Tron) are {@code AddressValidator}'s
 * own, later, separately-scheduled scope (L8, task 12). Until that task is wired in front of this
 * validator, callers must supply {@code contractAddress} in exactly the form the allowlist stores it.</p>
 *
 * <p><b>A {@code WARN} line on every {@code UNKNOWN_TOKEN} call is intentional, not a bug (Phase 9,
 * Kimi Phase 8 Issue 7).</b> If the allowlist is never seeded (or a chain has none), every single
 * {@code validate} call for it logs - this is R14's own "surfaced loudly" requirement working exactly
 * as intended; no rate-limiting/once-only suppression is added, since suppressing a real, ongoing
 * misconfiguration signal would work against that requirement, not just against noisy logs.</p>
 */
@Component
public class TokenValidator {

    private static final Logger logger = LoggerFactory.getLogger(TokenValidator.class);

    /** Phase 9 (Kimi Phase 8 Issue 8): duplicates the same two-chain set already independently
     * hardcoded in {@code ProviderProperties} and {@code FinalityProperties} (both T03, frozen) -
     * matches existing codebase precedent rather than introducing a new pattern. Deriving this from
     * {@code TokenAllowlistProperties} was considered and rejected: that reflects which tokens are
     * currently *configured*, not which chains this platform *supports* - a fresh deployment with only
     * Ethereum entries seeded must still recognize "TRON" as a valid, known chain. Worth consolidating
     * into one shared constant if a third chain is ever added; not done proactively here to avoid
     * touching T03's frozen files for a cross-cutting concern outside this task's own scope. */
    private static final Set<String> KNOWN_CHAINS = Set.of("ETHEREUM", "TRON");

    private final TokenAllowlistRepository repository;

    public TokenValidator(TokenAllowlistRepository repository) {
        this.repository = repository;
    }

    public Optional<TokenAllowlist> validate(String chain, String contractAddress) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(contractAddress, "contractAddress");
        if (!KNOWN_CHAINS.contains(chain)) {
            throw new IllegalArgumentException("Unrecognized chain: " + chain);
        }

        Optional<TokenAllowlist> match = repository.findCurrentVersionEntry(chain, contractAddress);
        if (match.isEmpty()) {
            logUnknownToken(chain, contractAddress);
        }
        return match;
    }

    private void logUnknownToken(String chain, String contractAddress) {
        logger.warn("Unknown token: chain={} contractAddress={} reason=UNKNOWN_TOKEN", chain, contractAddress);
    }
}
