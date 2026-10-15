package com.themistra.payment.common;

/**
 * T15 (R19). Thrown when the Crypto Service's {@code POST /attest} responds {@code 409} -
 * the signing gate itself was not satisfied (quorum/finality unmet, or a screening error on the
 * Crypto Service's own side), a fundamentally different condition from
 * {@link CryptoServiceUnavailableException} (network failure, timeout, {@code 5xx}, a malformed
 * response, or - per Phase 3, Kimi Finding 4's accepted disposition - a {@code 400}). Giving this
 * its own exception type now, rather than collapsing it into {@code
 * CryptoServiceUnavailableException}, avoids a breaking change later once a caller (T17's {@code
 * ReceiptService}) needs to tell "the service is down" apart from "the gate isn't satisfied yet."
 *
 * <p>The message carries the {@code 409} response body's text, best-effort (Phase 3, Kimi
 * Finding 3). <b>Callers should not expect this to contain a specific reason</b> such as "quorum
 * unmet" or "screening error": verified directly against the real crypto-service's own {@code
 * attest.AttestationRefusedException}, its message is always the fixed, generic string
 * {@code "Attestation preconditions not met for <chain>:<txHash>"} - a deliberate choice on that
 * side to avoid leaking internal state in an error response. The value carried here is still
 * useful for correlating which {@code chain:txHash} attempt was refused across service logs, just
 * not for distinguishing *why*.</p>
 */
public class AttestationRefusedException extends RuntimeException {

    public AttestationRefusedException(String message) {
        super(message);
    }
}
