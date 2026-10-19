package com.themistra.payment.receipt.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * T28 (frozen brief D1(b)). A deliberate violation: a second repository for {@link LedgerEntry}
 * that declares a {@code @Modifying} query. The rule identifies guarded repositories by domain
 * type, not by name, so this is caught as well as the real {@link LedgerEntryRepository} would
 * be.
 *
 * <p>{@code @NoRepositoryBean} keeps Spring Data from instantiating it in any test context that
 * component-scans this package. Never referenced by production code.</p>
 */
@NoRepositoryBean
interface RogueModifyingLedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    @Modifying
    @Query("UPDATE LedgerEntry e SET e.prevHash = :prevHash")
    int rewriteEveryPrevHash(String prevHash);
}
