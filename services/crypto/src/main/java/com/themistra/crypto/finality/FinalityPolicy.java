package com.themistra.crypto.finality;

import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.model.FinalityStatus;

/**
 * Per-chain finality decision (L4, design.md task 14): whether a transaction's block has reached the
 * point this chain's own consensus considers irreversible. Ethereum = beacon {@code finalized}
 * checkpoint (R6); Tron = solidified block (R7). Adding a chain adds a new implementation of this
 * interface - never a branch inside an existing one, and never a shared confirmation-count constant
 * (agents.md, L4).
 *
 * <p><b>Pure and stateless.</b> No RPC/network call, no persistence. The sole input,
 * {@link FinalityStatus}, is always the direct result of a prior, successful {@code
 * ChainAdapter.getFinalityStatus(txHash)} call - never a value a caller fabricates independently. A
 * {@code null} argument to {@link #isFinal} is therefore a caller bug, not a valid domain state
 * (mirrors {@code ChainAdapter.getFinalityStatus}'s own "caller error, not a sentinel case" contract
 * for a non-existent transaction), and implementations fail fast rather than returning a silent
 * {@code false}.</p>
 *
 * <p><b>Caller-routing responsibility.</b> {@link FinalityStatus} carries no chain discriminator of
 * its own - {@link #chain} identifies which chain an implementation is for (for a future dispatcher
 * that picks the right policy), but no implementation verifies that the {@link FinalityStatus} it is
 * given actually came from that same chain's adapter. Passing an Ethereum-derived status to the Tron
 * policy (or vice versa) is not detected here; the caller must route consistently.</p>
 *
 * <p><b>Trust boundary.</b> {@link #isFinal} does not re-validate {@link FinalityStatus}'s internal
 * consistency (e.g. that {@code finalizedBlockNumber} does not exceed {@code currentBlockNumber}) -
 * both real adapters already throw before ever constructing an inconsistent {@link FinalityStatus}, so
 * every value reaching this interface is already trusted, adapter-produced data.</p>
 */
public interface FinalityPolicy {

    Chain chain();

    boolean isFinal(FinalityStatus status);
}
