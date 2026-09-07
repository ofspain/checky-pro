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
 * <p><b>No error/health channel (Phase 9 Finding, T10).</b> This interface deliberately carries no
 * {@code onError}-style method — provider health/degradation (R5, "unhealthy, lagging, or repeatedly
 * disagreeing") is task 10's dedicated mechanism, not a concern of the observation-delivery path
 * itself. A future adapter implementation that needs to signal a dropped subscription does so through
 * whatever mechanism task 10 established ({@code ProviderHealthTracker}), not through this sink. In
 * particular, a transport failure inside an adapter's own polling loop is swallowed and retried
 * internally (T06/T07's own established behavior) and never surfaces here — the watcher's only signal
 * for "this provider didn't answer" is its own correlation-window timeout, not a thrown exception or a
 * callback invocation of any kind.</p>
 *
 * <p><b>{@code provider} and {@code rawResponseJson} (T16 Phase 3 Finding 1).</b> The original
 * single-argument {@code onObservation(TxResult)} could not satisfy L3/R4 ("every provider response is
 * persisted verbatim before the quorum decision"): {@code TxResult} is a normalized value object with
 * no provider identity and is never itself JSON-serialized (see its own Javadoc). This interface is not
 * one of `design.md` §4c's VERBATIM artifacts (only {@link ChainAdapter}'s own interface, the finality
 * table, DDL, event schemas, and the attest/watch API are), so extending it here — rather than the
 * VERBATIM-frozen {@link ChainAdapter#getTx} — is the correct, minimal-blast-radius fix. {@code
 * rawResponseJson} is each adapter's own best-effort JSON capture of what it parsed from the provider —
 * not guaranteed byte-identical to the original wire response, since both web3j and trident already
 * parse into typed objects before this service ever sees a response; true wire-level capture would
 * require redoing T06/T07's transport layer, out of this task's proportionate scope. This is a
 * disclosed, deliberate interpretation of "verbatim," not a silent shortfall.</p>
 */
public interface ObservationSink {

    void onObservation(String provider, TxResult result, String rawResponseJson);
}
