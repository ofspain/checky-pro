package com.themistra.payment.common.crypto;

/**
 * {@code GET /internal/v1/transactions/{chain}/{txHash}} 200 body — the shape crypto-service's own
 * {@code TransactionLookupService} returns (crypto R29, L16).
 *
 * <p>{@code outcome} is {@code AGREED} only when existence and every reported fact reached a 2-of-3
 * provider agreement and the token is allowlisted; {@code UNKNOWN_TOKEN} when the facts agreed but
 * the token is not allowlisted; {@code HELD} otherwise. <b>A {@code HELD} answer carries no
 * facts</b> — every field below except {@code chain}, {@code txHash} and {@code existence} is null
 * — so nothing downstream can act on a value the providers disputed.</p>
 *
 * <p>{@code amount} is token base units as a decimal <b>string</b>, never a JSON number (L7): a
 * 78-digit base-unit amount does not survive a double.</p>
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
}
