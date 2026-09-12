package com.themistra.crypto.screening;

/**
 * Counterparty wallet-risk / OFAC screening at attest time (L12, R21). Behind this interface until a
 * real vendor is chosen (Q2, {@code package.md} §11) - {@link FailClosedScreeningClient} is the only
 * implementation today.
 *
 * <p><b>Contract every implementation must honor.</b> A call to {@link #screen} persists exactly one
 * {@link ScreeningResult} row before returning, regardless of outcome - this is the audit trail L12
 * depends on, and it is the implementation's own responsibility, not something a caller must remember
 * to do separately (frozen brief Phase 4, Finding #2 disposition: no enforcing decorator/wrapper exists
 * for this - only one implementation exists today, and building one now would be premature
 * abstraction).</p>
 *
 * <p><b>Fail-closed semantics (L12-T19a, frozen brief Phase 4).</b> Only {@link ScreeningOutcome#CLEARED}
 * may ever lead to a signature downstream. {@link ScreeningOutcome#BLOCKED} and
 * {@link ScreeningOutcome#ERROR} are both fail-closed, non-retryable outcomes for a caller - {@code
 * ERROR} must never be treated as transient/retryable.</p>
 *
 * <p><b>Exceptions (L12-T19b, frozen brief Phase 4).</b> An exception thrown by {@link #screen}
 * (including a failed {@link ScreeningResult} persistence) is not caught internally and propagates to
 * the caller, which must treat it as fail-closed, identically to an {@code ERROR} return.</p>
 *
 * <p>{@code address} is used as-is: this interface performs no address-format validation
 * ({@code token.AddressValidator} owns that) and does not reject a malformed address.</p>
 */
public interface ScreeningClient {

    /**
     * @param chain   the chain the counterparty address belongs to
     * @param address the counterparty address to screen, used as-is (no format validation)
     * @param txHash  the transaction this screening attempt is associated with, or {@code null} if the
     *                counterparty is being screened before a specific transaction exists
     * @return the screening outcome; never {@code null}
     */
    ScreeningOutcome screen(String chain, String address, String txHash);
}
