package com.themistra.payment.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * T19; {@link #findByReceiptUuid} added for the merchant receipt read (FU-T29-1). Package-private,
 * like every other repository in this codebase. Read-only additions only:
 * {@code ArchitectureTest} bans delete and modifying queries here.
 */
interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findByReceiptUuid(UUID receiptUuid);

    /** Payment read (frontend Phase 1b, 2026-10-21): the receipts issued for these payments. */
    List<Receipt> findByPaymentIdIn(Collection<Long> paymentIds);
}
