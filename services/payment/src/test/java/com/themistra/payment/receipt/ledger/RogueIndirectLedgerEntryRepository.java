package com.themistra.payment.receipt.ledger;

import org.springframework.data.repository.NoRepositoryBean;

/**
 * T28 Phase 9 (Phase 7 S1, Kimi Phase 8 F1/F6). A deliberate violation: a {@link LedgerEntry}
 * repository, typed only through {@link RogueLedgerEntryRepositoryBase}, that declares a Spring
 * Data derived delete named {@code removeBy…}. Spring Data treats {@code removeBy} exactly like
 * {@code deleteBy}, and neither needs {@code @Modifying}. Never referenced by production code.
 */
@NoRepositoryBean
interface RogueIndirectLedgerEntryRepository extends RogueLedgerEntryRepositoryBase {

    long removeByReceiptId(Long receiptId);
}
