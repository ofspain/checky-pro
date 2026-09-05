package com.themistra.crypto.adapter;

import com.themistra.crypto.adapter.model.TxResult;

/**
 * The callback a {@code ChainAdapter.subscribeAddress} caller (watcher layer, task 16) supplies to
 * receive address-watch transaction observations as they occur.
 *
 * <p>Scoped to transaction observations only — {@code getTokenInfo}/{@code getFinalityStatus}
 * responses are not delivered through this sink; those remain direct request/response calls on
 * {@code ChainAdapter} itself.</p>
 *
 * <p><b>No error/health channel (Phase 9 Finding).</b> This interface deliberately carries no
 * {@code onError}-style method — provider health/degradation (R5, "unhealthy, lagging, or repeatedly
 * disagreeing") is task 10's dedicated mechanism, not a concern of the observation-delivery path
 * itself. A future adapter implementation that needs to signal a dropped subscription does so through
 * whatever mechanism task 10 establishes, not through this sink.</p>
 */
public interface ObservationSink {

    void onObservation(TxResult result);
}
