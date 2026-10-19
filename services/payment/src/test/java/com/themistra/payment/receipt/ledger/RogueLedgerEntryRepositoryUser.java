package com.themistra.payment.receipt.ledger;

import java.util.List;

/**
 * T28 (frozen brief D1, Phase 3 F5). A deliberate violation of
 * {@code ArchitectureTest.shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueries}. It
 * deletes ledger entries in the three forms the rule must catch: a direct call, a call inside a
 * lambda, and a method reference.
 *
 * <p>It lives in the test source tree's {@code receipt.ledger} package because
 * {@link LedgerEntryRepository} is package-private. {@code analyzedClasses()} excludes test
 * classes, so the real canary never sees it. Never referenced by production code.</p>
 */
class RogueLedgerEntryRepositoryUser {

    private final LedgerEntryRepository repository = null;

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
}
