package com.themistra.payment.receipt;

import java.util.List;

/**
 * T28 Phase 11 (Kimi Gap 3). A deliberate violation of
 * {@code ArchitectureTest.shouldForbidLedgerAndReceiptRepositoryDeletesAndModifyingQueries} on the
 * receipt side: deletes through {@link ReceiptRepository}, by a direct call and inside a lambda.
 * Lives in the test tree's {@code receipt} package because {@code ReceiptRepository} is
 * package-private. Never referenced by production code.
 */
class RogueReceiptRepositoryUser {

    private final ReceiptRepository repository = null;

    void deleteDirectly(Receipt receipt) {
        repository.delete(receipt);
    }

    void deleteInsideALambda(List<Long> ids) {
        ids.forEach(id -> repository.deleteById(id));
    }
}
