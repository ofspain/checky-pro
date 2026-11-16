package com.themistra.payment.chainverify.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * What the landing page renders. Deliberately the stored row, not crypto-service's answer
 * passed through: the page shows what was recorded, so what a visitor reads and what the database
 * holds cannot drift apart.
 *
 * <p>{@code amount} is a decimal string in token base units (L7). {@code amountDisplay} is the same
 * number scaled by the token's decimals for humans — computed for display only, never stored, and
 * null whenever the token is not one whose decimals we know.</p>
 */
public record VerifyResponse(
        UUID verificationId,
        String chain,
        String txHash,
        String outcome,
        String existence,
        Integer confirmations,
        String tokenContractAddress,
        String fromAddress,
        String toAddress,
        String amount,
        String amountDisplay,
        Instant requestedAt
) {
}
