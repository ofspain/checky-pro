package com.themistra.crypto.watch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigInteger;
import java.time.Instant;

/**
 * What the payment service asks us to watch for.
 *
 * <p>{@code callerReference} is the caller's own identifier — its invoice id — and serves as the
 * idempotency key. Asking the caller to invent a second identifier, or to remember one we returned
 * on a response it may never have received, both create failure modes it cannot recover from.
 *
 * <p>{@code expectedAmount} is base units. There is no default expiry: watching costs provider
 * calls for as long as it lives, and only the caller knows the invoice's lifetime.
 */
public record RegisterWatchRequest(

        @NotBlank
        @Size(max = 128)
        String callerReference,

        @NotBlank
        @Size(max = 64)
        String chainId,

        @NotBlank
        @Size(max = 128)
        String recipientAddress,

        @NotBlank
        @Size(max = 128)
        String tokenAddress,

        @NotNull
        BigInteger expectedAmount,

        @NotNull
        Instant expiresAt
) {
}
