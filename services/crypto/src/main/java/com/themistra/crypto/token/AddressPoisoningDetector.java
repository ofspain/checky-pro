package com.themistra.crypto.token;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;

/**
 * Address-poisoning detection (L9): flags a candidate address that closely resembles - but differs
 * from - a previously seen counterparty. A stateless `@Component` (mirrors {@link AddressValidator}'s
 * own T12 precedent) - no persistence, no injected dependency; the caller supplies whatever counterparty
 * history it has.
 *
 * <p><b>Scope (Phase 0/1/2): this is the pure comparison algorithm only.</b> No {@code Watch}
 * dependency, no persistence of counterparty history, no modification to {@code chain.observations} or
 * any event emission - none of those integration points exist yet in this codebase. L9's own "flag it
 * on the observation so it propagates downstream" is not fully satisfied by this class alone; the most
 * plausible future integration point is the watcher layer (task 16, the first task with both real
 * per-watch counterparty history and a live observation/event pipeline), which must call {@link
 * #detectPoisoning} and attach its result to an observation/event for L9 to be fully enforced
 * end-to-end (mirrors T08's own Amendment #10 and T11's {@code QuorumOutcome}-mapping deferral).</p>
 *
 * <p><b>Case-sensitive, unnormalized comparison.</b> No address-format normalization of any kind is
 * performed - matches {@link AddressValidator}/{@code TokenValidator}'s own established "exact string,
 * caller normalizes" convention in this same {@code token/} package. A legitimate, previously-seen
 * counterparty reappearing in a different casing (e.g., lowercase vs. checksummed) will NOT be
 * recognized as an exact match here and may be flagged as resembling-but-differing - callers must
 * normalize both the candidate and the history collection to one canonical casing before calling
 * {@link #detectPoisoning} if they want to avoid that false positive; this is caller responsibility,
 * not a defect in this class.</p>
 *
 * <p><b>No address validation.</b> A structurally malformed candidate that happens to share enough
 * characters with a real previous address will still be flagged. Callers wanting to exclude
 * structurally invalid candidates should run them through {@link AddressValidator} (T12) first - this
 * class does not do so itself.</p>
 *
 * <p><b>An arbitrary match, not a ranked "best match."</b> If more than one previously-seen address
 * resembles the candidate, {@link #detectPoisoning} returns one of them, chosen arbitrarily (whichever
 * is encountered first while iterating). Consumers must treat the result as a boolean "poisoning
 * suspected" signal, not a deterministic closest-match report.</p>
 */
@Component
public class AddressPoisoningDetector {

    /** Every EVM address begins with the literal "0x" and every Tron mainnet address begins with "T" -
     * a flat 4-character prefix threshold would only require 2 (EVM) or 3 (Tron) additional matching
     * characters beyond that universal prefix, far noisier than a genuine match implies. 6 restores 4
     * real matching hex digits for EVM ("0x" + 4 more) without making this detector chain-aware. A
     * future task may externalize this as configuration if operational experience demands a different
     * value; not done now, since nothing indicates that need yet. */
    private static final int PREFIX_MATCH_LENGTH = 6;

    /** Suffixes carry no chain-mandated shared prefix, so the noise concern above never applied here -
     * kept at the wallet-truncation-derived value (most wallet/explorer UIs show a handful of trailing
     * characters, e.g. "...abcd"). */
    private static final int SUFFIX_MATCH_LENGTH = 4;

    public Optional<String> detectPoisoning(String candidateAddress, Collection<String> previouslySeenAddresses) {
        if (candidateAddress == null || previouslySeenAddresses == null) {
            return Optional.empty();
        }
        for (String previous : previouslySeenAddresses) {
            if (previous == null || candidateAddress.equals(previous)) {
                // A null entry is skipped, not an error; an exact match is the same counterparty
                // reappearing, not poisoning (R17's own "but differs" clause).
                continue;
            }
            if (hasMatchingPrefix(candidateAddress, previous) || hasMatchingSuffix(candidateAddress, previous)) {
                return Optional.of(previous);
            }
        }
        return Optional.empty();
    }

    private boolean hasMatchingPrefix(String candidate, String previous) {
        return candidate.regionMatches(0, previous, 0, PREFIX_MATCH_LENGTH);
    }

    private boolean hasMatchingSuffix(String candidate, String previous) {
        // String.regionMatches returns false (never throws) for a negative offset or an out-of-range
        // length - verified by direct execution in Phase 5 - so a candidate or previous address
        // shorter than SUFFIX_MATCH_LENGTH is handled safely with no manual bounds-checking needed.
        return candidate.regionMatches(candidate.length() - SUFFIX_MATCH_LENGTH, previous,
                previous.length() - SUFFIX_MATCH_LENGTH, SUFFIX_MATCH_LENGTH);
    }
}
