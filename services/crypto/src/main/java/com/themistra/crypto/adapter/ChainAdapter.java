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
 */
public interface ChainAdapter {

    Chain chain();                                   // ETHEREUM | TRON

    TxResult getTx(String txHash);                   // provider-scoped; quorum compares across adapters

    TokenInfo getTokenInfo(String contractAddress);  // identity by address only (L7)

    Subscription subscribeAddress(String address, ObservationSink sink); // watcher layer

    FinalityStatus getFinalityStatus(String txHash); // evaluated against the per-chain FinalityPolicy (L4)
}
