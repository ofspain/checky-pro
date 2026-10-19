package com.themistra.payment.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * T19; {@link #findByReceiptUuid} added for the merchant receipt read (FU-T29-1). Package-private,
 * like every other repository in this codebase. Read-only additions only:
 * {@code ArchitectureTest} bans delete and modifying queries here.
 */
interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findByReceiptUuid(UUID receiptUuid);
}
