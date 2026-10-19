package com.themistra.payment.receipt;

/**
 * Thrown by {@link ReceiptReadService#getForMerchant} when the receipt doesn't exist or belongs to
 * another merchant. One exception for both causes (R28/L10), so the response gives no way to tell
 * them apart. Mirrors {@code invoice.InvoiceNotFoundException}.
 */
class ReceiptNotFoundException extends RuntimeException {
}
