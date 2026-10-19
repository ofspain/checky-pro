package com.themistra.payment;

import com.themistra.payment.archtestfixtures.RogueFloatingPointMoneyUser;
import com.themistra.payment.archtestfixtures.RogueKmsAndChainClientUser;
import com.themistra.payment.archtestfixtures.RoguePermitAllUser;
import com.themistra.payment.archtestfixtures.RogueUnmappedEntity;
import com.themistra.payment.common.ResourceServerConfig;
import com.themistra.payment.invoice.Invoice;
import com.themistra.payment.payment.RogueInvoiceEntityReferencer;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * This service's first {@code ArchitectureTest} (T05). <b>No {@code @ArchTest} fields or
 * {@code @AnalyzeClasses} annotation appear anywhere in this class — on purpose.</b> Confirmed by
 * this session's own prior empirical reproduction (see {@code services/notification}'s own
 * identical {@code ArchitectureTest}): a class containing even one {@code @ArchTest}-annotated
 * field causes this repository's Maven Surefire (3.5.3) to report {@code Tests run: 0} for the
 * entire class, silently swallowing every ordinary {@code @Test} method too.
 * {@code services/auth}'s and {@code services/crypto}'s own existing architecture tests use the
 * {@code @ArchTest}-field shape and are themselves silently non-enforced by this same mechanism —
 * a separate, already-logged, out-of-scope issue. This class avoids the whole failure mode by
 * declaring the rule as a plain {@code private static final ArchRule} (no annotation at all) and
 * relying exclusively on the plain {@code @Test} canaries below.
 */
class ArchitectureTest {

    private static final String ANALYZED_PACKAGE = "com.themistra.payment";

    private static final Set<String> FORBIDDEN_FLOATING_POINT_TYPES =
            Set.of("float", "double", "java.lang.Float", "java.lang.Double",
                    // Array forms (Phase 11 Gap 5) - ArchUnit reports array raw types using the
                    // JVM's own binary name convention, not "float[]"/"Double[]".
                    "[F", "[D", "[Ljava.lang.Float;", "[Ljava.lang.Double;");

    /**
     * L6: money is {@code NUMERIC}/{@code BigDecimal} only. Scans fields (including array-typed
     * fields, Phase 11 Gap 5), method return types, method parameters, and constructor
     * parameters (Phase 11 Gap 4 — a future DTO constructor like {@code
     * CreateInvoiceRequest(double amount)} would otherwise evade this rule entirely, since
     * {@code JavaClass.getMethods()} does not include constructors). A custom {@link
     * ArchCondition}, not the built-in {@code haveRawType}/{@code haveRawReturnType} DSL,
     * because no built-in ArchUnit predicate covers "any parameter has a forbidden type"
     * cleanly — one condition keeps all four checks consistent and keeps this rule a single
     * named test, per {@code package.md} §8.
     */
    private static final ArchRule shouldRejectFloatingPointMoneyTypes = classes()
            .that().resideInAPackage(ANALYZED_PACKAGE + "..")
            .should(new ArchCondition<JavaClass>(
                    "declare no float/double/Float/Double field, method return type, or method parameter") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass.getFields().forEach(field -> {
                        boolean violates = FORBIDDEN_FLOATING_POINT_TYPES.contains(field.getRawType().getName());
                        if (violates) {
                            events.add(new SimpleConditionEvent(field, false,
                                    "field " + field.getFullName() + " has forbidden floating-point type "
                                            + field.getRawType().getName()));
                        }
                    });
                    javaClass.getMethods().forEach(method -> {
                        if (FORBIDDEN_FLOATING_POINT_TYPES.contains(method.getRawReturnType().getName())) {
                            events.add(new SimpleConditionEvent(method, false,
                                    "method " + method.getFullName() + " returns forbidden floating-point type "
                                            + method.getRawReturnType().getName()));
                        }
                        method.getParameters().forEach(parameter -> {
                            if (FORBIDDEN_FLOATING_POINT_TYPES.contains(parameter.getRawType().getName())) {
                                events.add(new SimpleConditionEvent(method, false,
                                        "method " + method.getFullName() + " has a forbidden floating-point "
                                                + "parameter of type " + parameter.getRawType().getName()));
                            }
                        });
                    });
                    javaClass.getConstructors().forEach(constructor ->
                            constructor.getParameters().forEach(parameter -> {
                                if (FORBIDDEN_FLOATING_POINT_TYPES.contains(parameter.getRawType().getName())) {
                                    events.add(new SimpleConditionEvent(constructor, false,
                                            "constructor " + constructor.getFullName() + " has a forbidden "
                                                    + "floating-point parameter of type "
                                                    + parameter.getRawType().getName()));
                                }
                            }));
                }
            })
            .because("L6: money is NUMERIC/BigDecimal only - no float/double/Float/Double anywhere");

    /** T09 (L1/L12), mirroring {@code crypto-service}'s own {@code
     * CrossModuleEntityArchitectureTest.FEATURE_MODULES} exactly. This service's six real
     * top-level packages that own an {@code @Entity} today: {@code invoice} (T05),
     * {@code payment} (T09's own enum, now a real entity since T11's own {@code Payment}),
     * {@code events} (T04's own {@code OutboxEvent}), {@code verification} (T10's own
     * {@code VerificationRecord}), {@code consumer} (T11's own {@code ProcessedEvent}), and
     * {@code receipt} (T19's own {@code Receipt} and its {@code ledger} sub-package's
     * {@code LedgerEntry} - a sub-package's entity still belongs to its own top-level module,
     * {@code featureModuleOf}'s own {@code startsWith(modulePackage + ".")} check already
     * covers this, confirmed by the real violation this rule reported before this module was
     * added here).
     * <b>When adding a new top-level package under {@code com.themistra.payment}, add it here
     * too</b> — the fail-fast null check below means a class outside every module listed here is
     * flagged rather than silently unenforced. {@code events} is cross-cutting infrastructure, not
     * a business feature like the others — omitting it entirely would trip the fail-fast
     * "unmapped entity" branch against real, already-shipped code (confirmed by running this rule
     * against the real codebase before finalizing the list, not assumed), rather than against a
     * deliberate violation. Including it also means a future module that imports
     * {@code OutboxEvent} directly (bypassing {@code OutboxPublisher}'s own public API) would now
     * be caught. {@code common} is deliberately excluded (shared plumbing per {@code agents.md})
     * and given
     * no special-case, mirroring crypto's own identical choice — a {@code common}-package class
     * importing a feature module's entity is already, correctly, a violation. */
    private static final List<String> FEATURE_MODULES =
            List.of("invoice", "payment", "events", "verification", "consumer", "receipt");

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

    /** T09 (L1/L12), named test {@code shouldPreventCrossModuleEntityImports} (`package.md` §8),
     * mirroring {@code crypto-service}'s own identical rule exactly (itself mirroring
     * {@code auth-service}'s). Dependency-based ({@link JavaClass#getDirectDependenciesToSelf()}),
     * not access-based — catches a declared-but-unused field of the entity's own type, a real
     * cross-module coupling even if nothing ever calls a method on it ({@link
     * RogueInvoiceEntityReferencer}'s own never-read field is exactly this shape, deliberately).
     * No allowlist needed — unlike crypto-service, this service has zero pre-existing cross-module
     * entity dependencies to grandfather in. */
    private static final ArchRule shouldPreventCrossModuleEntityImports = classes()
            .that().areAnnotatedWith(Entity.class)
            .should(onlyBeAccessedFromTheSameFeatureModule())
            .because("L1/L12: no feature module may import an entity class from another feature module");

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
                    events.add(new SimpleConditionEvent(dependency, sameModule, dependency.getDescription()));
                });
            }
        };
    }

    /** T15 — L3: this service never touches a blockchain or KMS; the signature for a receipt is
     * obtained only by calling the Crypto Service's own {@code POST /attest} endpoint
     * ({@code CryptoClient}), never by signing locally or reading on-chain state directly. No
     * custom {@link ArchCondition} is needed here (unlike the two rules above) - this is exactly
     * the built-in {@code noClasses().should().dependOnClassesThat().resideInAPackage(...)} DSL
     * shape, confirmed against {@code crypto-service}'s own {@code
     * KmsSignerArchitectureTest.onlyKmsSignerMayUseTheKmsSigningSdk} rule, which bans the KMS SDK
     * package the identical way. Unlike that rule, this one has no allowlist at all - this
     * service has zero legitimate reason to ever import any of these three packages, so a
     * blanket ban across the whole service is correct, mirroring {@code
     * shouldRejectFloatingPointMoneyTypes}'s own blanket-ban shape rather than crypto's
     * narrow-exception one. None of {@code org.web3j}/{@code org.tron}/
     * {@code software.amazon.awssdk.services.kms} is an actual dependency of this module
     * (confirmed against {@code pom.xml} - unlike {@code crypto-service}, which legitimately
     * depends on all three) - the stub classes under those same package names in
     * {@code src/test/java} exist solely so {@link RogueKmsAndChainClientUser} can compile a
     * real violation to prove this rule against, without adding the real SDKs to this module. */
    private static final ArchRule shouldBanKmsAndChainClientImports = noClasses()
            .that().resideInAPackage(ANALYZED_PACKAGE + "..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.web3j..", "org.tron..", "software.amazon.awssdk.services.kms..")
            .because("L3: this service never touches a blockchain or KMS - the receipt signature "
                    + "is obtained only via CryptoClient's own POST /attest call");

    /** T23 (Phase 9, Kimi Phase 8 Finding 5, accepted) - every RFC 9457 {@code type} URI must be
     * a named {@link com.themistra.payment.common.ProblemTypes} constant, never an inline
     * {@code URI.create(...)} literal at a call site; that is the entire point of "a stable type
     * catalogue" (R29, agents.md). {@code doNotBelongToAnyOf(ProblemTypes.class)} is the
     * exclusion - {@code ProblemTypes} itself is the one class allowed to call
     * {@code URI.create}, since it IS the catalogue. */
    /** Phase 11 Kimi Gap 8, accepted: also bans {@code new URI(String)} directly, not just the
     * {@code URI.create(String)} static factory - {@link java.net.URI} has a public constructor
     * with an identical effect that the original rule didn't cover, a real, demonstrated way to
     * bypass it without ever calling {@code create}. */
    private static final ArchRule shouldNotInlineProblemTypeUris = noClasses()
            .that().resideInAPackage(ANALYZED_PACKAGE + "..")
            .and().doNotBelongToAnyOf(com.themistra.payment.common.ProblemTypes.class)
            .should().callMethod(java.net.URI.class, "create", String.class)
            .orShould().callConstructor(java.net.URI.class, String.class)
            .because("T23/R29: every problem-type URI must be a named ProblemTypes constant, "
                    + "never an inline literal (via URI.create or the URI(String) constructor) - "
                    + "the whole point of a stable type catalogue");

    /** T25 (L10). This mirrors the identical rule in {@code services/auth} and
     * {@code services/notification}. Only {@link ResourceServerConfig} may call
     * {@code permitAll()}. A stray call anywhere else would quietly add a public endpoint that
     * nobody documented.
     *
     * <p><b>Scope (Phase 4, F6/F1).</b> This rule is the stray-caller guard and nothing more.
     * {@code PublicEndpointsTest} guards the rest:
     * <ul>
     *   <li>the allowlist's contents ({@code PublicEndpoints.PATTERNS});</li>
     *   <li>the single scoped {@code permitAll()} inside {@code ResourceServerConfig}
     *       ({@code resourceServerConfigContainsNoPermitAllOutsidePublicEndpoints});</li>
     *   <li>runtime 401 behaviour.</li>
     * </ul>
     *
     * <p><b>Limits (F2).</b> It covers {@code permitAll()} only. {@code AuthorizedUrl.anonymous()}
     * and {@code WebSecurityCustomizer} {@code web.ignoring()} are not covered. This service uses
     * neither.
     *
     * <p><b>API coupling (F4).</b> The rule names Spring Security's
     * {@code AuthorizeHttpRequestsConfigurer.AuthorizedUrl} directly. If an upgrade renames it,
     * this rule and {@link RoguePermitAllUser} stop compiling, a loud failure that is easy to fix.
     * They can't silently stop enforcing. */
    private static final ArchRule shouldEnforcePublicEndpointAllowlist = noClasses()
            .that().resideInAPackage(ANALYZED_PACKAGE + "..")
            .and().doNotBelongToAnyOf(ResourceServerConfig.class)
            .should().callMethod(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class, "permitAll")
            .because("L10: every non-internal endpoint requires a valid JWT - the only "
                    + "unauthenticated paths are PublicEndpoints.PATTERNS, declared solely in "
                    + "ResourceServerConfig");

    private static JavaClasses analyzedClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ANALYZED_PACKAGE);
    }

    @Test
    void shouldRejectFloatingPointMoneyTypesAtArchUnit() {
        shouldRejectFloatingPointMoneyTypes.check(analyzedClasses());
    }

    @Test
    void shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild() {
        shouldPreventCrossModuleEntityImports.check(analyzedClasses());
    }

    @Test
    void shouldBanKmsAndChainClientImportsIsCheckedDuringStandardBuild() {
        shouldBanKmsAndChainClientImports.check(analyzedClasses());
    }

    @Test
    void shouldNotInlineProblemTypeUrisIsCheckedDuringStandardBuild() {
        shouldNotInlineProblemTypeUris.check(analyzedClasses());
    }

    @Test
    void shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild() {
        shouldEnforcePublicEndpointAllowlist.check(analyzedClasses());
    }

    /** The canary above only proves the rule passes on already-clean code - it says nothing
     * about whether it can ever actually fail. {@link RogueKmsAndChainClientUser} is a real
     * class, deliberately depending on all three banned SDK stand-ins, deliberately placed
     * under {@code archtestfixtures} (outside every real feature module, mirroring {@link
     * RogueFloatingPointMoneyUser}'s own placement) so this test exercises the actual violation
     * path without ever risking the real canary above for anyone else. This test builds its own,
     * separate, narrow {@code JavaClasses} set naming exactly the violating class. */
    @Test
    void shouldBanKmsAndChainClientImportsActuallyFailsAgainstAGenuineViolation() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importClasses(RogueKmsAndChainClientUser.class,
                        org.web3j.StubWeb3jClass.class,
                        org.tron.StubTronClass.class,
                        software.amazon.awssdk.services.kms.StubKmsClass.class);

        assertThatThrownBy(() -> shouldBanKmsAndChainClientImports.check(violatingClasses))
                .as("a class depending on web3j/tron/the KMS SDK must fail this rule")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RogueKmsAndChainClientUser");
    }

    /** The canary above only proves the rule passes on already-clean code - it says nothing
     * about whether it can ever actually fail. {@link RogueInvoiceEntityReferencer} is a real
     * class, deliberately violating the rule, deliberately placed <b>inside</b> a real feature
     * module ({@code payment}) so this test exercises the actual {@code entityModule !=
     * dependingModule} violation path - not the fail-fast null path a fixture outside every known
     * module would instead trip. This test builds its own, separate, narrow {@code JavaClasses}
     * set naming exactly the two classes the violation involves. */
    @Test
    void shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importClasses(RogueInvoiceEntityReferencer.class, Invoice.class);

        assertThatThrownBy(() -> shouldPreventCrossModuleEntityImports.check(violatingClasses))
                .as("a feature module importing another feature module's entity must fail this rule")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RogueInvoiceEntityReferencer")
                .hasMessageContaining("Invoice");
    }

    /** {@link RogueUnmappedEntity} is a real {@code @Entity}, deliberately placed outside every
     * package {@link #FEATURE_MODULES} lists (it lives under {@code archtestfixtures}, not
     * {@code invoice}/{@code payment}/{@code events}), proving the documented "fail loudly,
     * don't silently skip" behavior for an entity this rule doesn't know how to place. */
    @Test
    void shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule() {
        JavaClasses unmappedEntityOnly = new ClassFileImporter().importClasses(RogueUnmappedEntity.class);

        assertThatThrownBy(() -> shouldPreventCrossModuleEntityImports.check(unmappedEntityOnly))
                .as("an @Entity outside every FEATURE_MODULES entry must fail loudly, not be silently unenforced")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("does not reside in any module listed in FEATURE_MODULES");
    }

    /** The canary above only proves the rule passes on already-clean code - it says nothing
     * about whether it can ever actually fail. {@link RogueFloatingPointMoneyUser} deliberately
     * violates all five checked shapes (field, array field, method return type, method
     * parameter, constructor parameter — Phase 11 Gaps 4/5) in one class, so this test exercises
     * every violation path at once. */
    @Test
    void shouldRejectFloatingPointMoneyTypesAtArchUnitActuallyFailsAgainstAGenuineViolation() {
        JavaClasses violatingClasses = new ClassFileImporter().importClasses(RogueFloatingPointMoneyUser.class);

        assertThatThrownBy(() -> shouldRejectFloatingPointMoneyTypes.check(violatingClasses))
                .as("a float field, a double[] field, a float-returning method, a Double "
                        + "parameter, and a float constructor parameter must each fail this rule")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("field")
                .hasMessageContaining("amount")
                .hasMessageContaining("forbidden floating-point type double")
                .hasMessageContaining("amountHistory")
                .hasMessageContaining("getFloatAmount")
                .hasMessageContaining("acceptDoubleAmount")
                .hasMessageContaining("constructor")
                .hasMessageContaining("<init>(float)");
    }

    /** The canary above only proves the rule passes on already-clean code - it says nothing
     * about whether it can ever actually fail. {@link RogueInlineProblemTypeUser} is a real
     * class, deliberately calling {@code URI.create(...)} directly, so this test exercises the
     * actual violation path. */
    @Test
    void shouldNotInlineProblemTypeUrisActuallyFailsAgainstAGenuineViolation() {
        JavaClasses violatingClasses = new ClassFileImporter()
                .importClasses(com.themistra.payment.archtestfixtures.RogueInlineProblemTypeUser.class);

        assertThatThrownBy(() -> shouldNotInlineProblemTypeUris.check(violatingClasses))
                .as("a class other than ProblemTypes calling URI.create(...) must fail this rule")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RogueInlineProblemTypeUser");
    }

    /** The canary above only shows that the rule passes on clean code. It says nothing about
     * whether the rule can fail. {@link RoguePermitAllUser} is a real class outside
     * {@code ResourceServerConfig} that calls {@code AuthorizedUrl.permitAll()} on purpose, so
     * this test exercises the real violation path. */
    @Test
    void shouldEnforcePublicEndpointAllowlistActuallyFailsAgainstAGenuineViolation() {
        JavaClasses violatingClasses = new ClassFileImporter().importClasses(RoguePermitAllUser.class);

        assertThatThrownBy(() -> shouldEnforcePublicEndpointAllowlist.check(violatingClasses))
                .as("a class other than ResourceServerConfig calling permitAll() must fail this rule")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RoguePermitAllUser.openUp");
    }

    /** Phase 9 (Kimi Phase 8 F5, accepted). The real-code canary above passes only because the
     * rule excludes {@link ResourceServerConfig}. This test proves that exclusion is
     * load-bearing: the analyzed production classes really do contain
     * {@code ResourceServerConfig}'s {@code permitAll()} call, which sits inside a lambda.
     * Without it, an import that silently missed that call would make the canary pass
     * vacuously. Phase 6's manual mutation check (remove the exclusion, so the rule fails at
     * {@code ResourceServerConfig.java:57}) is encoded here as a permanent test. */
    @Test
    void shouldEnforcePublicEndpointAllowlistExclusionIsLoadBearing() {
        JavaClass resourceServerConfig = analyzedClasses().get(ResourceServerConfig.class);

        assertThat(resourceServerConfig.getMethodCallsFromSelf())
                .as("ResourceServerConfig must contain the one real AuthorizedUrl.permitAll() call")
                .anyMatch(call -> call.getName().equals("permitAll")
                        && call.getTargetOwner().isEquivalentTo(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class));
    }
}
