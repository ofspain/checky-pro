package com.themistra.payment.receipt;

/**
 * T18 (R23/L9). Where {@code ReceiptStore.store(...)} actually wrote the two S3 objects.
 * {@code receipts.s3_key} (`V1__payments_baseline.sql`) is a single column, but this task
 * writes two objects — a future caller (T19, not built yet) stores {@code s3KeyPrefix} there
 * and keeps {@code jsonKey}/{@code pdfKey} in memory for whatever else it needs in the same
 * call chain (Phase 3/4, Kimi Finding 1).
 */
public record ReceiptStorageLocation(String bucket, String s3KeyPrefix, String jsonKey, String pdfKey) {
}
