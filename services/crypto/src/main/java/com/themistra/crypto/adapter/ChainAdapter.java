package com.themistra.crypto.adapter;

import com.themistra.crypto.adapter.model.FinalityStatus;
import com.themistra.crypto.adapter.model.Subscription;
import com.themistra.crypto.adapter.model.TokenInfo;
import com.themistra.crypto.adapter.model.TxResult;

/**
 * The one contract every chain integration implements — real (EthereumAdapter/TronAdapter, tasks
 * 6/7) or a sidecar-backed translation shim (L14) or, in tests, {@code FakeChainAdapter}. Each
 * provider is one instance of this interface; the quorum module (task 9) fans a fact out across
 * multiple instances for the same chain and compares their answers. VERBATIM per design.md §4c.
 *
 * <p><b>Failure vs. a legitimate negative answer (Phase 9 Finding).</b> An unchecked exception means
 * the provider/transport itself could not answer the query at all (RPC error, timeout, malformed
 * response) — no method on this interface represents "unknown" via a sentinel or {@code null} value.
 * A transaction this provider has simply not observed yet is a normal, successful answer:
 * {@link #getTx} returns {@code TxResult(exists=false, ...)}, never an exception — R1 treats
 * existence as one of the quorum-checked facts, so a false answer must be exactly as first-class as
 * a true one. When {@code exists=false}, the remaining {@code TxResult} fields carry no meaningful
 * data (implementers return zero/null for them, never fabricated values).</p>
 *
 * <p>{@link #getFinalityStatus} assumes the caller already knows the transaction exists (via a prior
 * {@link #getTx} call) — finality has no meaning for a transaction that was never observed, so
 * calling it for one is a caller error, not a case this interface defines a sentinel for.</p>
 *
 * <p>{@link #getTokenInfo} has no awareness of this service's own signed token allowlist — it
 * returns whatever metadata the chain/provider itself reports for the contract address. Classifying
 * a contract as {@code UNKNOWN_TOKEN} (R14) is entirely {@code TokenValidator}'s job (task 11),
 * never this interface's.</p>
 */
public interface ChainAdapter {

    Chain chain();                                   // ETHEREUM | TRON

    TxResult getTx(String txHash);                   // provider-scoped; quorum compares across adapters

    TokenInfo getTokenInfo(String contractAddress);  // identity by address only (L7)

    Subscription subscribeAddress(String address, ObservationSink sink); // watcher layer

    FinalityStatus getFinalityStatus(String txHash); // evaluated against the per-chain FinalityPolicy (L4)
}
