package com.themistra.crypto.adapter.model;

/**
 * A live address-watch handle returned by {@code ChainAdapter.subscribeAddress} (watcher layer,
 * task 16). Cancelling stops further {@code ObservationSink} callbacks for that subscription.
 */
public interface Subscription {

    void cancel();
}
