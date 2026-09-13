package com.themistra.crypto.attest;

import archtestfixtures.RogueAttestReferencer;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The named test {@code shouldOnlyAllowAttestPathToInvokeKmsSign} (R22, L11, ADR-0004). Scoped to
 * {@code com.themistra.crypto} - never {@code com.themistra} - since {@code services/auth}'s own {@code
 * MfaSeedEncryption} legitimately uses the KMS SDK directly under its own, separately-governed ADR-0003
 * (frozen brief Phase 4, Finding #3, verified against that class's actual source before this scoping was
 * chosen).
 *
 * <p><b>Two rules, not one (Finding #2/#9).</b> {@link #onlyKmsSignerMayUseTheKmsSigningSdk} bans *any*
 * dependency on the whole KMS SDK package from every class except one whose simple name starts with
 * {@code "KmsSigner"} <i>and</i> which resides inside {@code com.themistra.crypto.attest} (Phase 9,
 * Kimi Phase 8 Finding #1 - the package constraint was added after independent review pointed out that
 * a name-only exception would let a hypothetically-named class like {@code
 * com.themistra.crypto.watch.KmsSignerWatcher} dodge the rule despite living outside {@code attest}). A
 * name-prefix exception, not a hardcoded list of exact class names, was chosen after discovering during
 * implementation that more than one legitimate test needs this exception - {@code KmsSignerTest} (mocks
 * {@link software.amazon.awssdk.services.kms.KmsClient} to unit-test {@code KmsSigner}) and {@code
 * KmsSignerLocalStackIntegrationTest} (a real KMS-API round-trip, mirroring {@code
 * ObservationSnapshotStoreLocalStackIntegrationTest}'s established pattern) both need it, and an
 * exact-name list would need editing every time a further legitimate {@code KmsSigner}-testing file is
 * added - a name-prefix rule instead keeps working for any future one without being re-opened, while
 * still catching a rogue, unrelated class anywhere in {@code com.themistra.crypto} (main or test) that
 * tries to use the KMS SDK (Finding #14). This ban already subsumes "only {@code KmsSigner} may call
 * {@code KmsClient.sign(...)} or build a {@code SignRequest}" (any such call necessarily requires
 * depending on {@code KmsClient}/{@code SignRequest}), and is simpler and more robust than a
 * method-call-specific {@code ArchCondition}. The underlying shape mirrors {@code services/auth}'s own
 * {@code only_MfaSeedEncryption_may_use_the_aws_sdk} rule, already proven to compile and run under this
 * identical parent POM/ArchUnit version - that service resolves its own analogous test's identical need
 * to mock {@code KmsClient} by excluding tests from the scan entirely ({@code
 * ImportOption.DoNotIncludeTests}); this task instead scans everything and uses a name-plus-package
 * exception, so a rogue test elsewhere is still caught.
 * {@link #noClassOutsideAttestMayReferenceKmsSigner} separately covers the literal "no package outside
 * attest may reference KmsSigner" half of the task statement, which the SDK-dependency ban alone does
 * not (a caller could depend on {@code KmsSigner} without ever depending on the KMS SDK directly).</p>
 *
 * <p><b>{@code @ArchTest} fields are documentation only (Finding #1).</b> Confirmed, via {@code
 * services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java}'s own already-documented
 * negative-proof finding, that this repository's Surefire configuration does not execute ArchUnit's
 * JUnit 5 {@code @ArchTest} engine - a deliberately-introduced violation would not fail {@code mvn
 * test}. The plain {@code @Test} canary below is what actually gates the build.</p>
 */
@AnalyzeClasses(packages = KmsSignerArchitectureTest.ANALYZED_PACKAGE)
class KmsSignerArchitectureTest {

    /** Single source of truth for the package scanned by both {@code @AnalyzeClasses} and the canary's
     * own {@link ClassFileImporter} below - Phase 9 (Kimi Phase 8 Finding #3), mirroring auth's own
     * {@code ArchitectureTest.ANALYZED_PACKAGE} lesson (Kimi Phase 11 Gap 1 there) so the two can never
     * silently drift apart. */
    static final String ANALYZED_PACKAGE = "com.themistra.crypto";

    @ArchTest
    static final ArchRule noClassOutsideAttestMayReferenceKmsSigner = noClasses()
            .that().resideOutsideOfPackage("com.themistra.crypto.attest..")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.themistra.crypto.attest.KmsSigner")
            .because("R22/L11: kms:Sign is reachable only from the attest module");

    @ArchTest
    static final ArchRule onlyKmsSignerMayUseTheKmsSigningSdk = noClasses()
            .that().haveSimpleNameNotStartingWith("KmsSigner")
            .or().resideOutsideOfPackage("com.themistra.crypto.attest..")
            .should().dependOnClassesThat().resideInAPackage("software.amazon.awssdk.services.kms..")
            .because("ADR-0004/L11: kms:Sign is invoked only from KmsSigner itself - no other class "
                    + "anywhere in com.themistra.crypto may depend on the KMS SDK at all, which "
                    + "necessarily covers building a SignRequest or calling KmsClient.sign(...) "
                    + "directly. The one exception is a class named KmsSigner* AND residing inside "
                    + "com.themistra.crypto.attest (KmsSigner itself, and its own tests) - Phase 9 "
                    + "(Kimi Phase 8 Finding #1): the package constraint closes the loophole where a "
                    + "class named e.g. KmsSignerWatcher outside attest could otherwise dodge this rule "
                    + "purely by naming convention");

    // Finding #14: no ImportOption.DoNotIncludeTests - both main and test sources are scanned, so a
    // test class outside attest importing the KMS SDK also fails the rule.
    private static final JavaClasses analyzedClasses = new ClassFileImporter()
            .importPackages(ANALYZED_PACKAGE);

    @Test
    void shouldOnlyAllowAttestPathToInvokeKmsSignIsCheckedDuringStandardBuild() {
        noClassOutsideAttestMayReferenceKmsSigner.check(analyzedClasses);
        onlyKmsSignerMayUseTheKmsSigningSdk.check(analyzedClasses);
    }

    /** Phase 11 (Kimi Test Review) Gap 1: the assertion above only proves the rules pass on already-
     * clean code - it says nothing about whether they can ever actually fail. {@link
     * RogueAttestReferencer} is a real class, deliberately violating both rules, deliberately placed
     * in a standalone {@code archtestfixtures} package outside {@code com.themistra.crypto} entirely so
     * it can never be swept up by {@link #analyzedClasses}'s own package-wide scan and never break the
     * real canary above for anyone else. This test builds its own, separate, narrow {@code JavaClasses}
     * set naming exactly the two classes the violation involves, and proves both rules genuinely throw
     * against it. {@code services/auth}'s own {@code MfaSeedEncryption} was considered as a ready-made
     * real violation instead of a new fixture class, but rejected: {@code services/crypto} has no
     * dependency on {@code services/auth} at all (confirmed by reading {@code
     * services/crypto/pom.xml}), and adding one - even test-scoped, even for this - would itself
     * violate {@code agents.md}'s "Services depend only on libs/ and contracts/ - never on another
     * service's source." */
    @Test
    void bothRulesActuallyFailAgainstAGenuineViolation() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importClasses(RogueAttestReferencer.class, KmsSigner.class);

        // Verified directly (a standalone diagnostic run): ArchRule.check(...) throws plain
        // java.lang.AssertionError on a violation, not some ArchUnit-specific subtype.
        assertThatThrownBy(() -> noClassOutsideAttestMayReferenceKmsSigner.check(violatingClasses))
                .as("a class outside attest depending on KmsSigner must fail this rule")
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> onlyKmsSignerMayUseTheKmsSigningSdk.check(violatingClasses))
                .as("a non-KmsSigner* class depending on the KMS SDK must fail this rule")
                .isInstanceOf(AssertionError.class);
    }
}
