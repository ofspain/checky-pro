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
 * <p><b>Current-version semantics.</b> A single, global "current version" governs the whole signed
 * allowlist artifact (not per-chain): {@link TokenAllowlistRepository#findTopByOrderByVersionDesc()}
 * finds the highest {@code version} present across the entire table, then the lookup is scoped to
 * exactly that version. A superseded version's rows remain for audit/history but are never active. An
 * entirely empty table means every lookup is {@code UNKNOWN_TOKEN} - a fail-loud, not fail-open,
 * default.</p>
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
 */
@Component
public class TokenValidator {

    private static final Logger logger = LoggerFactory.getLogger(TokenValidator.class);
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

        Optional<TokenAllowlist> latest = repository.findTopByOrderByVersionDesc();
        if (latest.isEmpty()) {
            logUnknownToken(chain, contractAddress);
            return Optional.empty();
        }

        Optional<TokenAllowlist> match = repository.findByChainAndContractAddressAndVersion(
                chain, contractAddress, latest.get().version());
        if (match.isEmpty()) {
            logUnknownToken(chain, contractAddress);
        }
        return match;
    }

    private void logUnknownToken(String chain, String contractAddress) {
        logger.warn("Unknown token: chain={} contractAddress={} reason=UNKNOWN_TOKEN", chain, contractAddress);
    }
}
