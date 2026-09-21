package com.themistra.crypto.common;

import com.themistra.crypto.attest.AttestationService;
import com.themistra.crypto.quorum.QuorumDecision;
import com.themistra.crypto.token.TokenAllowlist;
import com.themistra.crypto.watch.ChainCursor;
import com.themistra.crypto.watch.RogueWatchEntityReferencer;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The named test {@code shouldPreventCrossModuleEntityImports} (L15), mirroring
 * {@code services/auth/src/test/java/com/themistra/auth/ArchitectureTest}'s own identical rule
 * exactly (L15's own text: "Enforced by ArchUnit, mirroring the auth service") - T25 Phase 3/4
 * Finding #2, #3, #8.
 *
 * <p><b>{@code common} receives no exemption (Frozen Brief Finding #3).</b> {@code common} is
 * deliberately excluded from {@link #FEATURE_MODULES} and given no special-case in
 * {@link #onlyBeAccessedFromTheSameFeatureModule()} - a {@code common}-package class importing any
 * feature-module entity is already, correctly, a violation (its {@code featureModuleOf} resolves to
 * {@code null}, and {@code entityModule.equals(null)} is {@code false}), with zero additional code
 * needed. This matches auth's own unmodified behavior exactly, not a new design choice.</p>
 *
 * <p><b>Dependency-based, not access-based (mirrors auth's own documented rationale).</b>
 * {@link JavaClass#getDirectDependenciesToSelf()} catches a declared-but-unused field of the entity's
 * type - a real cross-module coupling even if nothing ever calls a method on it - which an
 * access-based check ({@code getAccessesToSelf()}) would miss entirely. {@link RogueWatchEntityReferencer}'s
 * own single, never-read field is exactly this shape, deliberately.</p>
 *
 * <p><b>{@code @ArchTest} fields are documentation only.</b> Confirmed for this repository at T20
 * (verified via a real negative-proof run - a deliberate violation did not fail {@code mvn test}
 * through the {@code @ArchTest} field alone). The plain {@code @Test} canary below is what actually
 * gates the build.</p>
 */
@AnalyzeClasses(packages = CrossModuleEntityArchitectureTest.ANALYZED_PACKAGE,
        importOptions = ImportOption.DoNotIncludeTests.class)
class CrossModuleEntityArchitectureTest {

    static final String ANALYZED_PACKAGE = "com.themistra.crypto";

    /** This branch's actual 11 feature-module packages (Phase 0/3, verified directly against this
     * branch's own package layout - not `main`'s separate, unrelated `feat`-stack packages).
     * {@code common} is deliberately excluded (L15: "Shared plumbing lives in common"). <b>When
     * adding a new top-level package under {@code com.themistra.crypto}, add it here too</b> - the
     * fail-fast null check below means a class outside every module listed here is flagged rather
     * than silently unenforced, so forgetting to update this list is caught by the build. */
    private static final List<String> FEATURE_MODULES = List.of(
            "adapter", "attest", "events", "finality", "observation", "provider", "quorum",
            "reorg", "screening", "token", "watch");

    /** T25 Phase 6/9: two pre-existing, deliberate cross-module entity couplings this codebase
     * already ships (T17-T21ish, predating this task's own rule), explicitly allowlisted rather
     * than either silently broken by the rule or requiring an out-of-scope production refactor to
     * decouple them. Mirrors auth's own {@code ALLOWED_CROSS_MODULE_CONTROLLER_SERVICE_DEPENDENCIES}
     * pattern. Keyed by fully-qualified name (not {@code Class.getName()} for every entry) because
     * {@link com.themistra.crypto.watch.Watcher} is package-private and cannot be referenced via a
     * class literal from this {@code common}-package test - its entry is spelled out as a string
     * literal instead, sacrificing that one entry's rename-safety for the other three's -
     * {@link #allowlistedCrossModuleEntityDependenciesStillExistInCode()} closes that gap anyway (a
     * rename makes its {@code analyzedClasses.get(...)} lookup throw, failing loudly). No tracking
     * issue exists in this repository today; both couplings are recorded here, in
     * {@code artifacts/06-implementation-notes.md}, and in {@code artifacts/12-specification-verification.md}
     * as candidates for a future decoupling task (passing a derived value/DTO instead of the entity
     * itself) - not fixed here, since production code changes are out of this task's own scope. */
    private static final Set<String> ALLOWED_CROSS_MODULE_ENTITY_DEPENDENCIES = Set.of(
            AttestationService.class.getName() + "->" + ChainCursor.class.getName(),
            "com.themistra.crypto.watch.Watcher->" + QuorumDecision.class.getName());

    private static String featureModuleOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        for (String module : FEATURE_MODULES) {
            String modulePackage = ANALYZED_PACKAGE + "." + module;
            if (packageName.equals(modulePackage) || packageName.startsWith(modulePackage + ".")) {
                return module;
            }
        }
        return null;
    }

    @ArchTest
    static final ArchRule shouldPreventCrossModuleEntityImports = classes()
            .that().areAnnotatedWith(Entity.class)
            .should(onlyBeAccessedFromTheSameFeatureModule())
            .because("L15: no feature module may import an entity class from another feature module");

    private static ArchCondition<JavaClass> onlyBeAccessedFromTheSameFeatureModule() {
        return new ArchCondition<JavaClass>("only be accessed from the same feature module") {
            @Override
            public void check(JavaClass entityClass, ConditionEvents events) {
                String entityModule = featureModuleOf(entityClass);
                if (entityModule == null) {
                    events.add(new SimpleConditionEvent(entityClass, false, entityClass.getName()
                            + " is annotated @Entity but does not reside in any module listed in "
                            + "FEATURE_MODULES - add its module there, or this entity's cross-module "
                            + "boundary is not being enforced at all"));
                    return;
                }
                entityClass.getDirectDependenciesToSelf().forEach(dependency -> {
                    JavaClass originClass = dependency.getOriginClass();
                    String dependingModule = featureModuleOf(originClass);
                    boolean sameModule = entityModule.equals(dependingModule);
                    boolean allowed = ALLOWED_CROSS_MODULE_ENTITY_DEPENDENCIES.contains(
                            originClass.getName() + "->" + entityClass.getName());
                    events.add(new SimpleConditionEvent(
                            dependency, sameModule || allowed, dependency.getDescription()));
                });
            }
        };
    }

    private static final JavaClasses analyzedClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(ANALYZED_PACKAGE);

    @Test
    void shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild() {
        shouldPreventCrossModuleEntityImports.check(analyzedClasses);
    }

    /** T25 Phase 3/4 Finding #2: the canary above only proves the rule passes on already-clean code -
     * it says nothing about whether it can ever actually fail. {@link RogueWatchEntityReferencer} is a
     * real class, deliberately violating the rule, deliberately placed <b>inside</b> a real feature
     * module ({@code watch}) so this test exercises the actual {@code entityModule != dependingModule}
     * violation path - not the fail-fast null path a fixture outside every known module would instead
     * trip (the mistake the original Phase 2 brief made, caught at Phase 3). This test builds its own,
     * separate, narrow {@code JavaClasses} set naming exactly the two classes the violation involves,
     * mirroring {@code KmsSignerArchitectureTest.bothRulesActuallyFailAgainstAGenuineViolation}'s
     * exact pattern. */
    @Test
    void shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importClasses(RogueWatchEntityReferencer.class, TokenAllowlist.class);

        assertThatThrownBy(() -> shouldPreventCrossModuleEntityImports.check(violatingClasses))
                .as("a feature module importing another feature module's entity must fail this rule")
                .isInstanceOf(AssertionError.class);
    }

    /** T25 Phase 9 (self-review Finding #1 / Kimi Phase 8 Findings #1-2): mirrors auth's own
     * {@code allowlistedControllerServiceDependenciesStillExistInCode} exactly - if a future refactor
     * ever removed either allowlisted dependency, its entry in
     * {@link #ALLOWED_CROSS_MODULE_ENTITY_DEPENDENCIES} would become silent dead configuration (the
     * rule would simply never have anything to apply it to, not fail) with nothing else noticing. This
     * fails loudly instead. Also closes Kimi Finding #2 for free: {@code Watcher}'s entry is a plain
     * string literal (package-private, no class-literal reference possible from this
     * {@code common}-package test), so a rename would silently stop matching the allowlist rather than
     * failing compilation - but {@code analyzedClasses.get("...Watcher")} below throws
     * {@code IllegalArgumentException} the moment that name no longer resolves to a real class,
     * failing this test loudly instead. */
    @Test
    void allowlistedCrossModuleEntityDependenciesStillExistInCode() {
        assertThat(hasDirectDependency(AttestationService.class, ChainCursor.class))
                .as("%s should still depend on %s - if this is no longer true, remove the now-stale "
                        + "allowlist entry for it", AttestationService.class.getSimpleName(),
                        ChainCursor.class.getSimpleName())
                .isTrue();

        JavaClass watcherClass = analyzedClasses.get("com.themistra.crypto.watch.Watcher");
        assertThat(watcherClass.getDirectDependenciesFromSelf().stream()
                        .anyMatch(dependency -> dependency.getTargetClass().getFullName()
                                .equals(QuorumDecision.class.getName())))
                .as("Watcher should still depend on %s - if this is no longer true, remove the now-stale "
                        + "allowlist entry for it", QuorumDecision.class.getSimpleName())
                .isTrue();
    }

    private static boolean hasDirectDependency(Class<?> origin, Class<?> target) {
        return analyzedClasses.get(origin).getDirectDependenciesFromSelf().stream()
                .anyMatch(dependency -> dependency.getTargetClass().getFullName().equals(target.getName()));
    }
}
