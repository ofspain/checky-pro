package com.themistra.payment.receipt;

/** A receipt request the caller got wrong (a missing or malformed {@code sub} claim). Maps to
 * {@code 400}, mirroring {@code invoice.InvalidInvoiceRequestException}. */
class InvalidReceiptRequestException extends RuntimeException {

    InvalidReceiptRequestException(String message) {
        super(message);
    }
}
