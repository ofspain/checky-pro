package com.themistra.payment.payment;

import com.themistra.payment.invoice.Invoice;

/**
 * T09 (Phase 3/4 Finding 2): a genuine, deliberate L1/L12 violation - a class inside the
 * {@code payment} feature module holding a field of type {@code invoice.Invoice}, a real
 * {@code @Entity} in a different real feature module. Deliberately placed <b>inside</b>
 * {@code com.themistra.payment.payment} (test-source, so the real production canary's own
 * package-wide scan never sweeps it up via {@code DoNotIncludeTests}) so {@code featureModuleOf}
 * correctly resolves it to {@code payment} - the whole point is to exercise the real
 * {@code entityModule != dependingModule} violation path, not the fail-fast null path a fixture
 * outside every known module would instead trip. Never referenced by any production code; the
 * field is declared, never read, which is itself the point (dependency-based checking, not
 * access-based, is required to catch exactly this shape).
 */
public class RogueInvoiceEntityReferencer {

    private final Invoice rogueCrossModuleEntityReference = null;
}
