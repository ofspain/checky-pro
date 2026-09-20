package com.themistra.crypto.chain;

/**
 * Namespaced chain identifier, CAIP-2 shaped: {@code <namespace>:<reference>}.
 *
 * <p>Launch chains are Tron and Ethereum (ARCHITECTURE §3.4), so a bare numeric EVM chain ID
 * cannot express half the set. The namespace also decides which address form is legal on a given
 * observation — EIP-55 checksummed hex for {@code eip155}, Base58 for Tron (§6.3).
 */
public record ChainId(String namespace, String reference) {

    public static final String EIP155 = "eip155";
    public static final String TRON = "tron";

    public ChainId {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("chain namespace is required");
        }
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("chain reference is required");
        }
        if (namespace.indexOf(':') >= 0 || reference.indexOf(':') >= 0) {
            throw new IllegalArgumentException("chain namespace and reference must not contain ':'");
        }
    }

    /** Parses the wire form, e.g. {@code eip155:1}. */
    public static ChainId parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("chain id is required");
        }
        int separator = value.indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException(
                    "chain id must be namespaced as <namespace>:<reference>, got: " + value);
        }
        return new ChainId(value.substring(0, separator), value.substring(separator + 1));
    }

    public static ChainId evm(long chainId) {
        return new ChainId(EIP155, Long.toString(chainId));
    }

    public boolean isEvm() {
        return EIP155.equals(namespace);
    }

    /** The wire form carried on every published event. */
    public String value() {
        return namespace + ":" + reference;
    }

    @Override
    public String toString() {
        return value();
    }
}
