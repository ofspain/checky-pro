package com.themistra.payment.receipt;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The outcome of one {@link ReceiptReconciler#reconcile()} run: how many receipts were checked,
 * and every discrepancy found. An empty {@code discrepancies} list means the database agrees with
 * the locked S3 originals.
 */
public record ReceiptReconciliationResult(int receiptsChecked, List<Discrepancy> discrepancies) {

    public ReceiptReconciliationResult {
        discrepancies = List.copyOf(Objects.requireNonNull(discrepancies, "discrepancies must not be null"));
    }

    public boolean consistent() {
        return discrepancies.isEmpty();
    }

    /**
     * One disagreement. {@code receiptUuid} is {@code null} for an S3 object that has no
     * {@code receipts} row at all.
     */
    public record Discrepancy(UUID receiptUuid, String s3KeyPrefix, String reason) {

        public Discrepancy {
            Objects.requireNonNull(s3KeyPrefix, "s3KeyPrefix must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
