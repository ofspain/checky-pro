package com.themistra.crypto.finality;

import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.model.FinalityStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Tron finality (R7): a transaction is final once its block is solidified - {@code
 * TronAdapter.getFinalityStatus} already fetches the current solidified block number (via {@code
 * NodeType.SOLIDITY_NODE}) into {@link FinalityStatus#finalizedBlockNumber()}; this class only
 * interprets the already-obtained value. R7's own "(~19 confirmations)" describes what a solidified
 * block typically represents - it is never re-derived as a literal threshold here; solidity is a
 * property the node itself reports, not a confirmation count this policy computes.
 *
 * <p><b>Q4 resolution (confirmation-count basis for {@code chain.tx.confirmed}, R9).</b> R9's event is
 * not built yet, but the basis it will use is already fixed by {@code TronAdapter.computeConfirmations}
 * (T07): block depth from the current head ({@code currentBlockNumber - txBlockNumber + 1}) - the
 * identical basis {@code EthereumAdapter.computeConfirmations} (T06) already uses for Ethereum, not
 * confirmations counted toward the solidified block specifically. This class's own finality check is a
 * separate concept from that confirmation count: finality compares {@code txBlockNumber} against the
 * *solidified* block, whereas the R9 confirmation count compares it against the *current* block.</p>
 *
 * <p>This class and {@link EthereumFinalityPolicy} implement the identical {@code
 * txBlockNumber <= finalizedBlockNumber} comparison today. That duplication is intentional and
 * required by L4 ("adding a chain adds a policy object") - see {@link FinalityPolicy}'s own class
 * Javadoc for the full rationale. Do not collapse these two classes into one on the basis that their
 * current bodies happen to match.</p>
 */
@Component
public class TronFinalityPolicy implements FinalityPolicy {

    @Override
    public Chain chain() {
        return Chain.TRON;
    }

    @Override
    public boolean isFinal(FinalityStatus status) {
        Objects.requireNonNull(status, "status");
        return status.txBlockNumber() <= status.finalizedBlockNumber();
    }
}
