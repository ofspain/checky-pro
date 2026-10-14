package com.themistra.payment.archtestfixtures;

import jakarta.persistence.Entity;

/**
 * T09 (Phase 3/4 Finding 2): a real {@code @Entity} class living outside every package
 * {@code ArchitectureTest.FEATURE_MODULES} knows about - deliberately placed in this existing
 * {@code archtestfixtures} package (test-source, already excluded from the real production
 * canary's own scan via {@code DoNotIncludeTests}), proving the rule's fail-fast behavior for an
 * unmapped entity actually fires, not just the entity-module-mismatch path
 * {@code RogueInvoiceEntityReferencer} already covers.
 */
@Entity
public class RogueUnmappedEntity {
}
