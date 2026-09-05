package com.themistra.crypto.adapter.model;

/**
 * A live address-watch handle returned by {@code ChainAdapter.subscribeAddress} (watcher layer,
 * task 16). Cancelling stops further {@code ObservationSink} callbacks for that subscription.
 */
public interface Subscription {

    /**
     * Idempotent — a second or later call is a no-op. Safe to call from any thread, not only the
     * one that received {@code subscribeAddress}'s return value. Does not guarantee that an
     * observation already in flight at the moment of cancellation is suppressed — only that no
     * further ones are delivered afterward.
     */
    void cancel();
}
