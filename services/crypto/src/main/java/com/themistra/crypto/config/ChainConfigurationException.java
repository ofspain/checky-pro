package com.themistra.crypto.config;

/**
 * A chain declaration that cannot safely be served.
 *
 * <p>Thrown during startup so the service refuses traffic. A deployment that cannot satisfy §6.1 —
 * too few providers, an unattributable answer, a chain nothing can observe — must fail loudly at
 * deploy time rather than run and later attest on evidence thinner than the architecture promises.
 *
 * <p>Messages name labels and chains, never endpoint values: a startup failure is exactly the sort
 * of message that ends up pasted into a ticket.
 */
public class ChainConfigurationException extends RuntimeException {

    public ChainConfigurationException(String message) {
        super(message);
    }
}
