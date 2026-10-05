package archtestfixtures;

import jakarta.persistence.Entity;

/**
 * T16 - a real {@code @Entity} class deliberately placed outside {@code com.themistra.notification}
 * entirely (mirroring {@code services/crypto}'s own identical fixture and package name), used only
 * to prove {@code ArchitectureTest.shouldPreventCrossModuleEntityImports}'s own fail-fast path - an
 * {@code @Entity} outside every known {@code FEATURE_MODULES} entry - actually fires, rather than
 * being silently unenforced. No {@code @Id}/JPA-mapping correctness is needed: ArchUnit inspects
 * only the bytecode annotation, and no persistence unit ever scans this test-only class.
 */
@Entity
public class RogueUnmappedEntity {

    private Long id;
}
