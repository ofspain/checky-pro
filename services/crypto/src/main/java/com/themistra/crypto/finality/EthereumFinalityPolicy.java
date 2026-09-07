package com.themistra.crypto.finality;

import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.model.FinalityStatus;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Ethereum finality (R6): a transaction is final once its block is at or below the beacon-chain
 * {@code finalized} checkpoint - never a fixed confirmation count. {@code
 * EthereumAdapter.getFinalityStatus} already fetches that checkpoint (via {@code
 * DefaultBlockParameterName.FINALIZED}) into {@link FinalityStatus#finalizedBlockNumber()}; this class
 * only interprets the already-obtained value.
 *
 * <p>This class and {@link TronFinalityPolicy} implement the identical {@code
 * txBlockNumber <= finalizedBlockNumber} comparison today. That duplication is intentional and
 * required by L4 ("adding a chain adds a policy object") - see {@link FinalityPolicy}'s own class
 * Javadoc for the full rationale. Do not collapse these two classes into one on the basis that their
 * current bodies happen to match.</p>
 */
@Component
public class EthereumFinalityPolicy implements FinalityPolicy {

    @Override
    public Chain chain() {
        return Chain.ETHEREUM;
    }

    @Override
    public boolean isFinal(FinalityStatus status) {
        Objects.requireNonNull(status, "status");
        return status.txBlockNumber() <= status.finalizedBlockNumber();
    }
}
