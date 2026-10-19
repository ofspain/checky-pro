package com.themistra.payment.common;

/**
 * Thrown by {@link CryptoServiceTokenProvider} or {@link CryptoClient} on any transport failure
 * or non-2xx response from the Crypto Service or auth-service's own token endpoint — an upstream
 * dependency failure, never the caller's fault. Mapped to {@code 503} by {@code
 * InvoiceExceptionHandler}.
 *
 * <p><b>Placement correction (Phase 6, disclosed):</b> the frozen brief (Phase 4) listed this
 * under the {@code invoice} package. Placing it there would make {@code common} (which actually
 * throws it, via {@link CryptoServiceTokenProvider}/{@link CryptoClient}) depend on a feature
 * module ({@code invoice}) — backward per L12/agents.md ("no feature module imports another
 * feature module's entity," and more fundamentally, {@code common} must never depend on any
 * feature module at all). Moved to {@code common} instead; {@code invoice}'s own exception
 * handler references it the correct direction (feature module depending on {@code common}).</p>
 *
 * <p><b>Latent risk, disclosed not fixed (T23 Phase 9, Kimi Phase 8 Finding 3):</b> despite
 * living in {@code common}, the only {@code @ExceptionHandler} for this exception today is
 * {@code InvoiceExceptionHandler.onCryptoServiceUnavailable} — {@code ExportController} and
 * every other current caller-adjacent module never calls {@link CryptoClient}, so this is
 * harmless today. If a future controller outside {@code invoice} ever calls {@code CryptoClient}
 * and it throws this exception, it will fall through to {@code ApiExceptionHandler}'s generic
 * catch-all and become a {@code 500 INTERNAL_ERROR}, silently losing both the correct
 * {@code 503} status and the stable {@code ProblemTypes.CRYPTO_SERVICE_UNAVAILABLE} type. Moving
 * the handler to {@code ApiExceptionHandler} was judged out of this task's own scope (R29 is
 * about the type catalogue, not handler reorganization) — the next task that adds a second
 * {@code CryptoClient} caller must either duplicate this handler or relocate it.</p>
 */
public class CryptoServiceUnavailableException extends RuntimeException {

    public CryptoServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
