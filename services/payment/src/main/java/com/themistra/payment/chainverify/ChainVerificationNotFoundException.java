package com.themistra.payment.chainverify;

/**
 * Thrown when a receipt link names a verification id that was never recorded. Mirrors
 * {@code receipt.ReceiptNotFoundException}: one exception, no detail, so a caller probing ids
 * learns nothing beyond "not here".
 */
class ChainVerificationNotFoundException extends RuntimeException {
}
