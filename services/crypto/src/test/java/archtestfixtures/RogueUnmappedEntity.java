package archtestfixtures;

import jakarta.persistence.Entity;

/**
 * T25 Phase 11 (Kimi Test Review) Finding #1: a real {@code @Entity} class living outside every
 * package {@code CrossModuleEntityArchitectureTest.FEATURE_MODULES} knows about - deliberately placed
 * in this standalone top-level {@code archtestfixtures} package (T20's own established location for
 * exactly this purpose: guaranteed outside {@code com.themistra.crypto} entirely, so the real
 * production canary's own package-wide scan can never sweep it up). Proves the rule's fail-fast
 * behavior for an unmapped entity actually fires, not just the entity-module-mismatch path
 * {@code RogueWatchEntityReferencer} already covers.
 */
@Entity
public class RogueUnmappedEntity {
}
