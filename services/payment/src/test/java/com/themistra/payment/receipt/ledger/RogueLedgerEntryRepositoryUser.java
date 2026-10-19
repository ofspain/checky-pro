package com.themistra.payment.receipt.ledger;

import java.util.List;

/**
 * T28 (frozen brief D1, Phase 3 F5). A deliberate violation of
 * {@code ArchitectureTest.shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueries}. It
 * deletes ledger entries in the three forms the rule must catch: a direct call, a call inside a
 * lambda, and a method reference. Phase 9 adds a derived {@code removeBy…} delete called on a
 * repository that is typed only through an intermediate interface.
 *
 * <p>It lives in the test source tree's {@code receipt.ledger} package because
 * {@link LedgerEntryRepository} is package-private. {@code analyzedClasses()} excludes test
 * classes, so the real canary never sees it. Never referenced by production code.</p>
 */
class RogueLedgerEntryRepositoryUser {

    private final LedgerEntryRepository repository = null;
    private final RogueIndirectLedgerEntryRepository indirectRepository = null;

    void deleteDirectly(LedgerEntry entry) {
        repository.delete(entry);
    }

    void deleteInsideALambda(List<Long> ids) {
        ids.forEach(id -> repository.deleteById(id));
    }

    /** Compiles to a method handle owned by {@code CrudRepository}, not by
     * {@code LedgerEntryRepository}: javac records the declaring type for a method reference. */
    void deleteByMethodReference(List<LedgerEntry> entries) {
        entries.forEach(repository::delete);
    }

    /** Phase 9 (S1/F1/F6): {@code remove…} is a delete too, and this repository is guarded only
     * through its super-interface's type argument. */
    void removeThroughADerivedQuery(Long receiptId) {
        indirectRepository.removeByReceiptId(receiptId);
    }
}
