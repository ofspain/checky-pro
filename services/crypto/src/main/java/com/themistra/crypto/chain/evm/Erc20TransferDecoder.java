package com.themistra.crypto.chain.evm;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Decodes an ERC-20 {@code Transfer} log into values.
 *
 * <p>Deliberately free of web3j types and of any I/O: this is where the bugs that would corrupt a
 * payment actually live — a mis-sliced topic silently attributes a payment to the wrong address,
 * and a mis-parsed {@code data} word silently changes the amount. Keeping it pure means both are
 * reachable in a unit test rather than only against a live chain.
 *
 * <p>Layout of {@code Transfer(address indexed from, address indexed to, uint256 value)}:
 * topic[0] is the event signature hash, topic[1] and topic[2] are the two indexed addresses
 * left-padded into 32-byte words, and the single non-indexed {@code value} occupies {@code data}.
 */
public final class Erc20TransferDecoder {

    /** keccak256("Transfer(address,address,uint256)") */
    public static final String TRANSFER_SIGNATURE =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private static final int WORD_HEX_LENGTH = 64;
    private static final int ADDRESS_HEX_LENGTH = 40;

    private Erc20TransferDecoder() {
    }

    /**
     * Decodes one log, or returns empty when it is not an ERC-20 {@code Transfer}.
     *
     * <p>Returning empty rather than throwing is deliberate: a receipt routinely carries logs from
     * other contracts, and those are not errors — they are simply not the event we watch for.
     *
     * @param contractAddress the address that emitted the log
     * @param topics          indexed event fields, signature hash first
     * @param data            ABI-encoded non-indexed fields
     */
    public static Optional<Erc20Transfer> decode(String contractAddress, List<String> topics, String data) {
        if (contractAddress == null || topics == null || topics.size() != 3) {
            return Optional.empty();
        }
        if (!TRANSFER_SIGNATURE.equalsIgnoreCase(topics.get(0))) {
            return Optional.empty();
        }

        Optional<String> from = addressFromTopic(topics.get(1));
        Optional<String> to = addressFromTopic(topics.get(2));
        if (from.isEmpty() || to.isEmpty()) {
            return Optional.empty();
        }

        Optional<BigInteger> value = uint256(data);
        return value.map(amount ->
                new Erc20Transfer(lowerHex(contractAddress), from.get(), to.get(), amount));
    }

    /**
     * Pulls a 20-byte address out of a 32-byte topic word.
     *
     * <p>A malformed or truncated topic yields empty rather than a silently wrong address — the
     * failure mode this method exists to prevent.
     */
    static Optional<String> addressFromTopic(String topic) {
        String hex = strip(topic);
        if (hex == null || hex.length() != WORD_HEX_LENGTH || !isHex(hex)) {
            return Optional.empty();
        }
        return Optional.of("0x" + hex.substring(WORD_HEX_LENGTH - ADDRESS_HEX_LENGTH).toLowerCase());
    }

    /** Reads the single 32-byte word in {@code data} as an unsigned integer. */
    static Optional<BigInteger> uint256(String data) {
        String hex = strip(data);
        if (hex == null || hex.isEmpty() || !isHex(hex)) {
            return Optional.empty();
        }
        // A Transfer carries exactly one non-indexed word. Anything longer is a different event
        // whose signature happened to collide, or a malformed log; either way, do not guess.
        if (hex.length() != WORD_HEX_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(new BigInteger(hex, 16));
    }

    private static String strip(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.regionMatches(true, 0, "0x", 0, 2) ? trimmed.substring(2) : trimmed;
    }

    private static String lowerHex(String address) {
        return address.trim().toLowerCase();
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hexDigit = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hexDigit) {
                return false;
            }
        }
        return true;
    }
}
