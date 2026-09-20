package com.themistra.crypto.quorum;

import com.themistra.crypto.chain.TxObservation;

import java.time.Instant;
import java.util.Optional;

/**
 * Exactly what one provider said, kept verbatim.
 *
 * <p>ARCHITECTURE §6.1 requires provider responses be logged so that any past attestation can be
 * re-derived and defended. That obligation is why a failed or dissenting answer is recorded as a
 * first-class value rather than discarded on the way to a decision.
 *
 * @param providerLabel non-sensitive provider label — never an endpoint or credential
 * @param observation   what the provider saw, empty when it reported absence or failed
 * @param failure       why the provider could not answer, empty when it did
 */
public record ProviderAnswer(
        String providerLabel,
        Optional<TxObservation> observation,
        Optional<String> failure,
        Instant observedAt
) {

    public ProviderAnswer {
        if (providerLabel == null || providerLabel.isBlank()) {
            throw new IllegalArgumentException("providerLabel is required");
        }
        if (observation == null || failure == null) {
            throw new IllegalArgumentException("observation and failure must be non-null Optionals");
        }
        if (observation.isPresent() && failure.isPresent()) {
            throw new IllegalArgumentException("an answer cannot be both an observation and a failure");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt is required");
        }
    }

    public static ProviderAnswer saw(String providerLabel, TxObservation observation, Instant at) {
        return new ProviderAnswer(providerLabel, Optional.of(observation), Optional.empty(), at);
    }

    /** The provider answered, and it does not see the transaction. That is a vote, not a failure. */
    public static ProviderAnswer absent(String providerLabel, Instant at) {
        return new ProviderAnswer(providerLabel, Optional.empty(), Optional.empty(), at);
    }

    /** The provider could not answer. This is not a vote in either direction. */
    public static ProviderAnswer failed(String providerLabel, String reason, Instant at) {
        return new ProviderAnswer(providerLabel, Optional.empty(),
                Optional.of(reason == null ? "unknown error" : reason), at);
    }

    public boolean isFailure() {
        return failure.isPresent();
    }

    /** True when the provider answered that the transaction is not there. */
    public boolean isAbsence() {
        return observation.isEmpty() && failure.isEmpty();
    }
}
