package com.themistra.crypto.adapter;

import com.themistra.crypto.adapter.model.TxResult;

/**
 * The callback a {@code ChainAdapter.subscribeAddress} caller (watcher layer, task 16) supplies to
 * receive address-watch transaction observations as they occur.
 *
 * <p>Scoped to transaction observations only — {@code getTokenInfo}/{@code getFinalityStatus}
 * responses are not delivered through this sink; those remain direct request/response calls on
 * {@code ChainAdapter} itself.</p>
 */
public interface ObservationSink {

    void onObservation(TxResult result);
}
