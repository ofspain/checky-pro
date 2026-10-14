package com.themistra.payment.invoice;

/**
 * Thrown by {@link InvoiceService#getForMerchant} when the invoice doesn't exist or isn't
 * owned by the caller — deliberately the same exception for both causes (R28/L10), so a caller
 * probing invoice UUIDs they don't own can't distinguish "doesn't exist" from "exists but isn't
 * yours" (no enumeration oracle). Mirrors {@code auth-service}'s own
 * {@code SessionNotFoundException} exactly.
 */
class InvoiceNotFoundException extends RuntimeException {
}
