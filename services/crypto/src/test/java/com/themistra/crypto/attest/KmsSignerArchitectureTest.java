package com.themistra.crypto.attest;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The named test {@code shouldOnlyAllowAttestPathToInvokeKmsSign} (R22, L11, ADR-0004). Scoped to
 * {@code com.themistra.crypto} - never {@code com.themistra} - since {@code services/auth}'s own {@code
 * MfaSeedEncryption} legitimately uses the KMS SDK directly under its own, separately-governed ADR-0003
 * (frozen brief Phase 4, Finding #3, verified against that class's actual source before this scoping was
 * chosen).
 *
 * <p><b>Two rules, not one (Finding #2/#9).</b> {@link #onlyKmsSignerMayUseTheKmsSigningSdk} bans *any*
 * dependency on the whole KMS SDK package from every class whose simple name does not start with {@code
 * "KmsSigner"}. A name-prefix exception, not a hardcoded list of exact class names, was chosen after
 * discovering during implementation that more than one legitimate test needs this exception - {@code
 * KmsSignerTest} (mocks {@link software.amazon.awssdk.services.kms.KmsClient} to unit-test {@code
 * KmsSigner}) and {@code KmsSignerLocalStackIntegrationTest} (a real KMS-API round-trip, mirroring
 * {@code ObservationSnapshotStoreLocalStackIntegrationTest}'s established pattern) both need it, and an
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
 * ImportOption.DoNotIncludeTests}); this task instead scans everything and uses a name-based exception,
 * so a rogue test elsewhere is still caught.
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
@AnalyzeClasses(packages = "com.themistra.crypto")
class KmsSignerArchitectureTest {

    @ArchTest
    static final ArchRule noClassOutsideAttestMayReferenceKmsSigner = noClasses()
            .that().resideOutsideOfPackage("com.themistra.crypto.attest..")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.themistra.crypto.attest.KmsSigner")
            .because("R22/L11: kms:Sign is reachable only from the attest module");

    @ArchTest
    static final ArchRule onlyKmsSignerMayUseTheKmsSigningSdk = noClasses()
            .that().haveSimpleNameNotStartingWith("KmsSigner")
            .should().dependOnClassesThat().resideInAPackage("software.amazon.awssdk.services.kms..")
            .because("ADR-0004/L11: kms:Sign is invoked only from KmsSigner itself - no other class "
                    + "anywhere in com.themistra.crypto may depend on the KMS SDK at all, which "
                    + "necessarily covers building a SignRequest or calling KmsClient.sign(...) "
                    + "directly. Every class named KmsSigner* (KmsSigner itself, and its own tests) "
                    + "is the one exception - a name-prefix, not a hardcoded exact-name list");

    // Finding #14: no ImportOption.DoNotIncludeTests - both main and test sources are scanned, so a
    // test class outside attest importing the KMS SDK also fails the rule.
    private static final JavaClasses analyzedClasses = new ClassFileImporter()
            .importPackages("com.themistra.crypto");

    @Test
    void shouldOnlyAllowAttestPathToInvokeKmsSignIsCheckedDuringStandardBuild() {
        noClassOutsideAttestMayReferenceKmsSigner.check(analyzedClasses);
        onlyKmsSignerMayUseTheKmsSigningSdk.check(analyzedClasses);
    }
}
