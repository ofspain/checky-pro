package com.themistra.crypto.watch;

import com.themistra.crypto.chain.ChainAddress;
import com.themistra.crypto.chain.ChainAdapterRegistry;
import com.themistra.crypto.chain.ChainId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Registering, retrieving and cancelling watches.
 *
 * <p>Rejects at registration anything that could never match — an unconfigured chain, a malformed
 * address, a non-positive amount, an expiry already past. Each of those would otherwise become a
 * watch that sits there costing provider calls and presenting, to a merchant, as a payment that
 * never arrived.
 */
@Service
public class WatchService {

    private final WatchRepository repository;
    private final ChainAdapterRegistry registry;
    private final Clock clock;

    public WatchService(WatchRepository repository, ChainAdapterRegistry registry, Clock clock) {
        this.repository = repository;
        this.registry = registry;
        this.clock = clock;
    }

    /**
     * Registers a watch, or returns the existing one for this caller reference.
     *
     * @throws WatchException.Invalid  when the request could never produce a workable watch
     * @throws WatchException.Conflict when the reference names a watch with different terms
     */
    @Transactional
    public Watch register(String callerReference, String chainIdValue, String recipientAddress,
                          String tokenAddress, BigInteger expectedAmount, Instant expiresAt) {

        require(callerReference != null && !callerReference.isBlank(), "callerReference is required");
        require(expectedAmount != null && expectedAmount.signum() > 0,
                "expectedAmount must be a positive base-unit integer");
        require(expiresAt != null, "expiresAt is required");

        Instant now = clock.instant();
        require(expiresAt.isAfter(now), "expiresAt must be in the future");

        ChainId chainId = parseChain(chainIdValue);
        if (!registry.supports(chainId)) {
            throw new WatchException.Invalid(
                    "chain " + chainId + " is not configured — a watch on it could never be observed");
        }

        String recipient = normalise(chainId, recipientAddress, "recipientAddress");
        String token = normalise(chainId, tokenAddress, "tokenAddress");

        Optional<Watch> existing = repository.findByCallerReference(callerReference);
        if (existing.isPresent()) {
            Watch watch = existing.get();
            if (!watch.hasSameTermsAs(chainId.value(), recipient, token, expectedAmount)) {
                throw new WatchException.Conflict(callerReference);
            }
            return watch;
        }

        return repository.save(new Watch(
                UUID.randomUUID(), callerReference, chainId.value(),
                recipient, token, expectedAmount, expiresAt, now));
    }

    @Transactional(readOnly = true)
    public Watch findByUuid(UUID watchUuid) {
        return repository.findByWatchUuid(watchUuid)
                .orElseThrow(() -> new WatchException.NotFound("uuid " + watchUuid));
    }

    /** Active watches for a chain — active meaning unexpired as of now. */
    @Transactional(readOnly = true)
    public List<Watch> activeWatches(ChainId chainId) {
        return repository.findActive(chainId.value(), clock.instant());
    }

    /** Cancelling an already-cancelled watch succeeds: a caller retrying should not have to care. */
    @Transactional
    public void cancel(UUID watchUuid) {
        Watch watch = findByUuid(watchUuid);
        if (watch.getStatus() != WatchStatus.CANCELLED) {
            watch.cancel(clock.instant());
            repository.save(watch);
        }
    }

    private ChainId parseChain(String value) {
        try {
            return ChainId.parse(value);
        } catch (IllegalArgumentException e) {
            throw new WatchException.Invalid("chainId is invalid: " + e.getMessage());
        }
    }

    private String normalise(ChainId chainId, String address, String field) {
        try {
            return ChainAddress.normalise(chainId, address);
        } catch (IllegalArgumentException e) {
            throw new WatchException.Invalid(field + " is not valid for chain " + chainId);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new WatchException.Invalid(message);
        }
    }
}
