package com.themistra.payment.payment;

/**
 * Thrown by {@link PaymentStateMachine#transition} for any {@code (current, target)} pair
 * outside the fixed, LOCKED (L1) edge set — never a silent no-op. Public (not package-private,
 * unlike {@code InvoiceNotFoundException}'s own precedent): a future cross-package caller (e.g.
 * task 11's {@code consumer} package) must be able to catch or reference it.
 */
public class IllegalPaymentStateTransitionException extends RuntimeException {

    private final PaymentState current;
    private final PaymentState target;

    public IllegalPaymentStateTransitionException(PaymentState current, PaymentState target) {
        super("Illegal payment state transition: " + current + " -> " + target + " is not permitted");
        this.current = current;
        this.target = target;
    }

    public PaymentState getCurrent() {
        return current;
    }

    public PaymentState getTarget() {
        return target;
    }
}
