package com.themistra.crypto.chain;

/**
 * Address validation and normalisation, per chain namespace (ARCHITECTURE §6.3).
 *
 * <p>EVM addresses are 20 bytes of hex; Tron addresses are Base58 and begin with {@code T}. A
 * single address form applied across namespaces would accept an address that can never match on
 * the chain it was registered for — which presents as a payment that never arrives, the hardest
 * class of bug to diagnose from a support ticket.
 *
 * <p>Normalisation exists so that matching an observation against a watch does not depend on the
 * casing a caller happened to send. The checksummed form is what we publish; the normalised form
 * is what we store and index.
 */
public final class ChainAddress {

    private static final int EVM_HEX_LENGTH = 40;

    private ChainAddress() {
    }

    /** Whether this address is well-formed for the chain's namespace. */
    public static boolean isValid(ChainId chainId, String address) {
        if (chainId == null || address == null) {
            return false;
        }
        String trimmed = address.trim();
        if (chainId.isEvm()) {
            return isEvmAddress(trimmed);
        }
        if (ChainId.TRON.equals(chainId.namespace())) {
            return isTronAddress(trimmed);
        }
        return false;
    }

    /**
     * The stored, comparable form.
     *
     * @throws IllegalArgumentException when the address is not valid for the chain
     */
    public static String normalise(ChainId chainId, String address) {
        if (!isValid(chainId, address)) {
            throw new IllegalArgumentException(
                    "not a valid address for chain " + chainId + ": " + address);
        }
        String trimmed = address.trim();
        // EVM hex is case-insensitive, so lowercase is the canonical comparable form. Tron's
        // Base58 alphabet is case-significant and must be left exactly as given.
        return chainId.isEvm() ? trimmed.toLowerCase() : trimmed;
    }

    private static boolean isEvmAddress(String address) {
        if (!address.regionMatches(true, 0, "0x", 0, 2)) {
            return false;
        }
        String hex = address.substring(2);
        if (hex.length() != EVM_HEX_LENGTH) {
            return false;
        }
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            boolean hexDigit = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hexDigit) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTronAddress(String address) {
        if (address.length() != 34 || address.charAt(0) != 'T') {
            return false;
        }
        // Base58: no 0, O, I or l, to remove characters people confuse when reading an address aloud.
        for (int i = 0; i < address.length(); i++) {
            if ("0OIl".indexOf(address.charAt(i)) >= 0 || !Character.isLetterOrDigit(address.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
