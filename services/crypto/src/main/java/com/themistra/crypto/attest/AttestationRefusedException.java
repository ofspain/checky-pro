package com.themistra.crypto.attest;

/**
 * R23/L12: an attest request refused because a gate other than an active sanctions hit failed - a
 * required fact not {@code AGREED}, finality not met, no screenable counterparty found, or a screening
 * {@code ERROR}/thrown exception (L12's own fail-closed contract). Never thrown for a KMS/infrastructure
 * failure during signing itself (Phase 4, Finding #10/L-T21b) - that propagates as-is, surfacing as
 * {@code 500}, a deliberately different signal from this exception's {@code 409}.
 *
 * <p>The message is always the fixed, generic format below (Phase 4, Finding #15) - the constructor
 * accepts only {@code chain}/{@code txHash}, never a fact-type or failure-reason parameter, so there is
 * no code path that could ever construct a more detailed, potentially internal-state-leaking message
 * ({@code agents.md}: "no internal detail" in error responses).</p>
 */
class AttestationRefusedException extends RuntimeException {

    AttestationRefusedException(String chain, String txHash) {
        super("Attestation preconditions not met for " + chain + ":" + txHash);
    }
}
