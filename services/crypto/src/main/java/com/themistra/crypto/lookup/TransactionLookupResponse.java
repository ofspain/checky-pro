package com.themistra.crypto.lookup;

/**
 * {@code GET /internal/v1/transactions/{chain}/{txHash}} 200 body (design.md §4c, R29, L16).
 *
 * <p>{@code existence} is the 2-of-3 outcome for the transaction existing. {@code outcome} is the
 * outcome for the whole answer: {@code AGREED} only when existence and every reported fact reached a
 * 2-of-3 agreement and the token is allowlisted; {@code UNKNOWN_TOKEN} when the facts agreed but the
 * token is not on the allowlist (R14); {@code HELD} otherwise (L2). A {@code HELD} answer carries no
 * facts, so a caller can never act on a disputed value. {@code amount} is token base units as a
 * decimal string, never a JSON number.</p>
 */
public record TransactionLookupResponse(
        String chain,
        String txHash,
        String existence,
        Integer confirmations,
        String tokenContractAddress,
        String fromAddress,
        String toAddress,
        String amount,
        String outcome
) {

    static TransactionLookupResponse held(String chain, String txHash, String existence) {
        return new TransactionLookupResponse(chain, txHash, existence, null, null, null, null, null, "HELD");
    }
}
