package com.themistra.crypto.chain.evm;

/**
 * A provider could not answer.
 *
 * <p>Distinct from a provider answering "not on chain": the quorum layer treats this as no vote at
 * all, so an unreachable provider neither blocks a read the other two agree on nor counts toward
 * agreement (ARCHITECTURE §6.1).
 */
public class ProviderUnavailableException extends RuntimeException {

    private final String providerLabel;

    public ProviderUnavailableException(String providerLabel, String message, Throwable cause) {
        super(providerLabel + ": " + message, cause);
        this.providerLabel = providerLabel;
    }

    public String providerLabel() {
        return providerLabel;
    }
}
