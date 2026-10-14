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
 */
public class CryptoServiceUnavailableException extends RuntimeException {

    public CryptoServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
