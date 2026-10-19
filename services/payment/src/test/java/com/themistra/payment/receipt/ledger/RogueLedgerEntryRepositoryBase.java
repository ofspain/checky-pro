package com.themistra.payment.receipt.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * T28 Phase 9 (Kimi Phase 8 F6). An intermediate repository interface, so
 * {@link RogueIndirectLedgerEntryRepository} reaches {@code JpaRepository<LedgerEntry, Long>} only
 * through it. Proves the guarded-repository check walks the interface hierarchy instead of
 * reading direct super-interfaces only. Never referenced by production code.
 */
@NoRepositoryBean
interface RogueLedgerEntryRepositoryBase extends JpaRepository<LedgerEntry, Long> {
}
