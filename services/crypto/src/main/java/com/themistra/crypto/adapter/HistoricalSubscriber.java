package com.themistra.crypto.adapter;

import com.themistra.crypto.adapter.model.Subscription;

import java.time.Instant;

/**
 * Implemented by chain adapters that can start an address subscription from a point in time, not
 * just from the chain head (2026-10-21, found by the infra/local Docker stack).
 *
 * <p>{@link ChainAdapter#subscribeAddress} starts scanning at the head block current when it is
 * called. A watcher only subscribes on the registry's next reconciliation tick, and again after every
 * restart or shard handover, so a transfer mined between watch registration and that subscription
 * was never scanned and its payment was silently missed. A subscription made through this interface
 * instead starts at the first block mined at or after {@code since} (the watch's own creation time),
 * so nothing between registration and subscription is skipped. Re-observing a transaction a previous
 * watcher already decided is harmless: duplicate quorum decisions are rejected and emit no event.</p>
 *
 * <p>Kept separate from {@link ChainAdapter}, whose signature is frozen by the design (§4c).</p>
 */
public interface HistoricalSubscriber {

    /** Like {@link ChainAdapter#subscribeAddress}, but the first scan starts at the first block whose
     * timestamp is at or after {@code since}. The start block is resolved on the first successful
     * poll tick, so a provider outage at subscribe time only delays the scan, never skips blocks. */
    Subscription subscribeAddressSince(String address, Instant since, ObservationSink sink);
}
