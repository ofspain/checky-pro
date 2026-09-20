package com.themistra.crypto.watch;

/** A {@code POST /internal/v1/watches} (R18) request that fails validation {@code @Valid} bean
 * validation on {@code RegisterWatchRequest} cannot express - {@code expectedAmount} format,
 * {@code expiresAt} in the past (requires the injected {@code Clock}), or a structurally invalid
 * {@code address}/{@code tokenContractAddress} for the request's chain (requires {@code
 * AddressValidator}, L8). Mapped to {@code 400} by {@link WatchExceptionHandler}; the message is
 * always a caller-facing validation reason, never internal/stack-trace detail (agents.md). */
class InvalidWatchRequestException extends RuntimeException {

    InvalidWatchRequestException(String message) {
        super(message);
    }
}
