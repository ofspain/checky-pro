package com.themistra.auth;

import com.themistra.auth.account.AccountController;
import com.themistra.auth.account.AdminAccountController;
import com.themistra.auth.authn.LockoutService;
import com.themistra.auth.common.PublicEndpoints;
import com.themistra.auth.token.SecurityChainsConfig;
import com.themistra.auth.token.SessionService;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
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
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compiles every module-boundary invariant this service has accumulated (D-004, D-017, D-018,
 * D-020, D-021) into a permanent, CI-enforced check. Each rule below exists because a specific
 * design decision said "no module may do X" — this class is where that stops being a comment
 * and starts being something that fails the build.
 */
@AnalyzeClasses(packages = ArchitectureTest.ANALYZED_PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Single source of truth for the package {@code @AnalyzeClasses} scans — also used by
     * {@link #analyzedClasses()} so the two can never silently drift apart (Kimi Phase 11 Gap 1). */
    static final String ANALYZED_PACKAGE = "com.themistra.auth";

    /** T35 Phase 4 D2 — the immediate subpackages of {@code com.themistra.auth} that contain
     * domain code. {@code common} and same-module subpackages (e.g. {@code account.dto}) are not
     * separate modules for {@link #featureModuleOf(JavaClass)}'s purposes. <b>When adding a new
     * top-level package under {@code com.themistra.auth}, add it here too</b> (Kimi Phase 8
     * Finding 4) — {@link #shouldPreventCrossModuleEntityImports} and
     * {@link #controllersDependOnlyOnTheirOwnModuleServices} both now fail fast (Phase 9,
     * Findings 1/8) if an {@code @Entity}/{@code @RestController} class is found outside every
     * module listed here, so forgetting to update this list is caught by the build, not silently
     * missed. See {@code spec/auth-service/agents.md}'s package-layout section. */
    private static final List<String> FEATURE_MODULES = List.of(
            "account", "authn", "authz", "audit", "token", "mfa", "apikey", "events",
            "cleanup", "ratelimit");

    /** T35 Phase 9 Finding 2: {@code common} is deliberately not in {@link #FEATURE_MODULES} — it
     * is checked separately in {@link #dependOnlyOnServicesFromTheSameFeatureModuleOrAnAllowedException()}
     * because its entire design purpose (L12/{@code agents.md}: "shared plumbing lives in
     * common") is to be usable by every module, unlike a feature module's own services. */
    private static final String COMMON_PACKAGE = "com.themistra.auth.common";

    /** T35 Phase 4 D2 — the two pre-existing, deliberate controller-to-another-module-service
     * dependencies this codebase already ships (T14/R20, T28), explicitly allowlisted rather than
     * silently broken by {@link #controllersDependOnlyOnTheirOwnModuleServices}. Keyed by
     * {@code Class.getName()} rather than string literals (Kimi Phase 8 Finding 3) so a future
     * rename of any of these four classes breaks compilation here, instead of silently
     * invalidating the allowlist and making a real rename look like a new violation. */
    private static final Set<String> ALLOWED_CROSS_MODULE_CONTROLLER_SERVICE_DEPENDENCIES = Set.of(
            AccountController.class.getName() + "->" + SessionService.class.getName(),
            AdminAccountController.class.getName() + "->" + LockoutService.class.getName());

    /** Returns the feature-module name (e.g. {@code "account"}) a class belongs to, per
     * {@link #FEATURE_MODULES}, or {@code null} if it isn't inside any of them (e.g. {@code
     * common} or top-level code). */
    private static String featureModuleOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        for (String module : FEATURE_MODULES) {
            String modulePackage = "com.themistra.auth." + module;
            if (packageName.equals(modulePackage) || packageName.startsWith(modulePackage + ".")) {
                return module;
            }
        }
        return null;
    }

    private static boolean isInCommonModule(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return packageName.equals(COMMON_PACKAGE) || packageName.startsWith(COMMON_PACKAGE + ".");
    }

    /**
     * T35 Phase 4 D1b: generalizes what was originally an Account-only rule
     * ({@code only_the_account_module_may_touch_the_Account_entity}) to every {@code @Entity}
     * class in every feature module — the original's {@code resideOutsideOfPackage(...)} (no
     * {@code ..} wildcard) also had a real bug incorrectly flagging {@code account.dto}/{@code
     * account.event}, fixed here by computing each entity's own module instead of hardcoding one.
     */
    @ArchTest
    static final ArchRule shouldPreventCrossModuleEntityImports = classes()
            .that().areAnnotatedWith(Entity.class)
            .should(onlyBeAccessedFromTheSameFeatureModule())
            .because("L12: no feature module may import an entity class from another feature "
                    + "module");

    private static ArchCondition<JavaClass> onlyBeAccessedFromTheSameFeatureModule() {
        return new ArchCondition<JavaClass>("only be accessed from the same feature module") {
            @Override
            public void check(JavaClass entityClass, ConditionEvents events) {
                String entityModule = featureModuleOf(entityClass);
                if (entityModule == null) {
                    // T35 Phase 9 Findings 1/8: fail fast rather than silently enforcing nothing —
                    // every @Entity class must live in a module FEATURE_MODULES already knows
                    // about; if this fires, either the entity is misplaced or FEATURE_MODULES
                    // itself needs updating for a new module, and either way the build should say
                    // so rather than silently stop checking this entity's cross-module boundary.
                    events.add(new SimpleConditionEvent(entityClass, false, entityClass.getName()
                            + " is annotated @Entity but does not reside in any module listed in "
                            + "FEATURE_MODULES — add its module there, or this entity's cross-"
                            + "module boundary is not being enforced at all"));
                    return;
                }
                // Dependency-based (getDirectDependenciesToSelf), not access-based
                // (getAccessesToSelf) — a field/parameter of the entity's type is already a real
                // cross-module coupling even if nothing ever calls a method on it; access-based
                // tracking only sees actual get/put/invoke bytecode and would miss a
                // declared-but-unused field of the entity's type entirely (caught by this task's
                // own negative-proof: an access-based first attempt let exactly that through).
                entityClass.getDirectDependenciesToSelf().forEach(dependency -> {
                    String dependingModule = featureModuleOf(dependency.getOriginClass());
                    boolean satisfied = entityModule.equals(dependingModule);
                    events.add(new SimpleConditionEvent(dependency, satisfied, dependency.getDescription()));
                });
            }
        };
    }

    /**
     * T35 Phase 4 D2: no controller may depend on another module's {@code @Service}, except the
     * two pre-existing, deliberate exceptions in {@link #ALLOWED_CROSS_MODULE_CONTROLLER_SERVICE_DEPENDENCIES}
     * and any {@code @Service} in {@code common} (Phase 9 Finding 2 — {@code common}'s entire
     * purpose is to be shared, so a controller using a common service isn't the coupling this
     * rule exists to prevent). {@code @RestControllerAdvice} classes are excluded from this rule
     * for free — confirmed via bytecode that {@code @RestControllerAdvice} is meta-annotated
     * {@code @AliasFor(annotation = ControllerAdvice.class)}, never {@code @RestController}, so
     * {@code areAnnotatedWith(RestController.class)} never selects one. Detection is deliberately
     * narrow (Kimi Phase 8 Findings 6/7): controllers are exactly {@code @RestController}
     * classes, services are exactly {@code @Service}-annotated classes — a future plain
     * {@code @Controller} or a service-layer class using only {@code @Component} would not be
     * seen by this rule; every controller/service in this codebase uses these two annotations
     * today.
     */
    @ArchTest
    static final ArchRule controllersDependOnlyOnTheirOwnModuleServices = classes()
            .that().areAnnotatedWith(RestController.class)
            .should(dependOnlyOnServicesFromTheSameFeatureModuleOrAnAllowedException())
            .because("L12: a controller reaching into another module's service layer without a "
                    + "defined boundary is the same class of coupling L12 forbids at the entity "
                    + "level — two pre-existing, deliberate exceptions are explicitly allowlisted "
                    + "and common services are always allowed, neither silently broken by this "
                    + "rule; service-to-service cross-module dependencies (e.g. LockoutService -> "
                    + "AccountService) are a separate, accepted, out-of-scope pattern this rule "
                    + "does not constrain");

    private static ArchCondition<JavaClass> dependOnlyOnServicesFromTheSameFeatureModuleOrAnAllowedException() {
        return new ArchCondition<JavaClass>(
                "depend only on services from the same feature module, common, or an allowed exception") {
            @Override
            public void check(JavaClass controllerClass, ConditionEvents events) {
                String controllerModule = featureModuleOf(controllerClass);
                if (controllerModule == null) {
                    // T35 Phase 9 Finding 1: same fail-fast symmetry as the entity rule — a
                    // @RestController outside every known module means FEATURE_MODULES itself
                    // needs updating, not that this rule should quietly stop checking it.
                    events.add(new SimpleConditionEvent(controllerClass, false, controllerClass.getName()
                            + " is annotated @RestController but does not reside in any module "
                            + "listed in FEATURE_MODULES — add its module there, or this "
                            + "controller's service dependencies are not being enforced at all"));
                    return;
                }
                controllerClass.getDirectDependenciesFromSelf().forEach(dependency -> {
                    JavaClass targetClass = dependency.getTargetClass();
                    if (!targetClass.isAnnotatedWith(Service.class)) {
                        return;
                    }
                    if (isInCommonModule(targetClass)) {
                        events.add(new SimpleConditionEvent(dependency, true, dependency.getDescription()));
                        return;
                    }
                    String serviceModule = featureModuleOf(targetClass);
                    boolean sameModule = controllerModule.equals(serviceModule);
                    boolean allowed = ALLOWED_CROSS_MODULE_CONTROLLER_SERVICE_DEPENDENCIES.contains(
                            controllerClass.getName() + "->" + targetClass.getName());
                    events.add(new SimpleConditionEvent(
                            dependency, sameModule || allowed, dependency.getDescription()));
                });
            }
        };
    }

    @ArchTest
    static final ArchRule authz_never_depends_on_the_account_module = noClasses()
            .that().resideInAPackage("com.themistra.auth.authz..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.themistra.auth.account", "com.themistra.auth.account..")
            .because("authz operates purely on account UUIDs, keyed on accounts.account_uuid, "
                    + "specifically to avoid this dependency (D-017)");

    @ArchTest
    static final ArchRule audit_never_depends_on_the_account_module = noClasses()
            .that().resideInAPackage("com.themistra.auth.audit..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.themistra.auth.account", "com.themistra.auth.account..")
            .because("audit records account_uuid only, never the internal account id (D-020)");

    @ArchTest
    static final ArchRule events_module_stays_domain_agnostic = noClasses()
            .that().resideInAPackage("com.themistra.auth.events..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.themistra.auth.account..", "com.themistra.auth.authz..",
                    "com.themistra.auth.audit..", "com.themistra.auth.token..")
            .because("the outbox is meant to be extractable to libs/java/outbox verbatim (D-018); "
                    + "feature modules own their own event payload shapes and call in with a "
                    + "plain Object, events never imports a domain type to understand it");

    @ArchTest
    static final ArchRule repositories_are_never_public = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .and().resideInAPackage("com.themistra.auth..")
            .and().areInterfaces()
            .should().notHaveModifier(JavaModifier.PUBLIC)
            .because("every module's repositories are accessed only through that module's own "
                    + "service class — this is already enforced by Java's package-private "
                    + "visibility today; this rule keeps it that way permanently");

    @ArchTest
    static final ArchRule only_token_module_references_public_endpoints = noClasses()
            .that().resideOutsideOfPackages(
                    "com.themistra.auth.token", "com.themistra.auth.common")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.themistra.auth.common.PublicEndpoints")
            .because("the CI-enforceable unauthenticated-path list is consumed by exactly one "
                    + "security configuration (SecurityChainsConfig) — see gap-analysis §2's "
                    + "reference-project lesson on 'temporary' unauthenticated whitelists");

    @ArchTest
    static final ArchRule only_MfaSeedEncryption_may_use_the_aws_sdk = noClasses()
            .that().doNotHaveFullyQualifiedName("com.themistra.auth.mfa.MfaSeedEncryption")
            .should().dependOnClassesThat().resideInAPackage("software.amazon.awssdk..")
            .because("D-010 forbids AWS SDK code in this service except the one narrow, named "
                    + "exception ADR-0003/D-025 carve out for TOTP-seed KMS envelope encryption, "
                    + "confined to mfa.MfaSeedEncryption and nowhere else");

    @ArchTest
    static final ArchRule cleanup_job_never_depends_on_repositories_directly = noClasses()
            .that().resideInAPackage("com.themistra.auth.cleanup..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .because("the T30 cleanup job resolves the account/token module-boundary tension "
                    + "(Phase 4 D1) by calling VerificationTokenService/RefreshTokenTracker only "
                    + "— it must never depend on either module's repository directly, even though "
                    + "the repositories_are_never_public rule above already keeps both "
                    + "package-private");

    @ArchTest
    static final ArchRule admin_controller_handlers_require_preauthorize = methods()
            .that().arePublic()
            .and().areDeclaredInClassesThat().haveSimpleNameStartingWith("Admin")
            .and().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should().beAnnotatedWith(PreAuthorize.class)
            .because("this is the endpoint-authentication sweep D-023 deferred until real admin "
                    + "controllers existed (D-024): every handler in an Admin* controller must "
                    + "explicitly declare its required role. The reference project's 'testing "
                    + "only' permitAll on /api/roles/** (gap-analysis §2) was exactly this "
                    + "failure one layer up; this rule catches the authorization-layer version "
                    + "of the same mistake — an admin method that forgot its @PreAuthorize");

    @ArchTest
    static final ArchRule shouldEnforcePublicEndpointAllowlist = noClasses()
            .that().doNotBelongToAnyOf(SecurityChainsConfig.class)
            .should().callMethod(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class, "permitAll")
            .because("L11 (task 32): SecurityChainsConfig is the only class that may declare an "
                    + "unauthenticated path, and it must do so only via PublicEndpoints — a stray "
                    + "permitAll() anywhere else would silently create an undocumented public "
                    + "endpoint (gap-analysis §2's 'testing only' whitelist lesson)");

    @Test
    void apiKeysTokenExchangeIsInThePublicAllowlist() {
        assertThat(PublicEndpoints.METHOD_SCOPED)
                .contains(new PublicEndpoints.MethodScoped(HttpMethod.POST, "/api-keys/token"));
    }

    /**
     * Kimi Phase 8 Finding 2: ArchUnit's own JUnit 5 engine does not execute under this project's
     * Maven Surefire setup today (confirmed via a real negative-proof run — a deliberately
     * introduced violation did not fail {@code mvn test}), so {@code @ArchTest} fields alone do
     * not currently gate a real build. This plain {@code @Test} (JUnit Jupiter, proven to execute
     * under Surefire) re-imports the same classes {@code @AnalyzeClasses} would and directly
     * invokes {@link #shouldEnforcePublicEndpointAllowlist}'s own {@code check(...)}, so this
     * task's specific named rule is enforced by {@code mvn test} regardless of the separate,
     * out-of-scope Surefire/ArchUnit engine-integration gap. Deliberately scoped to only this one
     * rule (Kimi Phase 11 Gap 2) — every other pre-existing {@code @ArchTest} rule in this file
     * (a count deliberately not restated here — Kimi Phase 8 Finding 5 caught it going stale the
     * first time this file grew) remains un-gated by {@code mvn test} until that separate, broader
     * issue is fixed; generalizing this workaround to cover all of them was judged out of scope
     * for this task.
     */
    @Test
    void shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild() {
        shouldEnforcePublicEndpointAllowlist.check(analyzedClasses());
    }

    /** Lazily imported once and reused by every canary (Kimi Phase 8 Finding 10 — three canaries
     * each separately re-importing the whole analyzed package was wasted, linearly-scaling work);
     * still the exact same scan configuration {@code @AnalyzeClasses} declares, so no canary can
     * silently check a different class set than the annotation-driven rules do (Kimi Phase 11
     * Gap 1). JUnit Jupiter runs this class's test methods sequentially by default (no parallel
     * execution configured anywhere in this project), so the lazy-init here needs no
     * synchronization. */
    private static JavaClasses analyzedClasses;

    private static JavaClasses analyzedClasses() {
        if (analyzedClasses == null) {
            analyzedClasses = new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages(ANALYZED_PACKAGE);
        }
        return analyzedClasses;
    }

    /** T35: same non-execution gap as T32 (ArchUnit's own JUnit 5 engine doesn't run under this
     * project's {@code mvn test}) — this canary re-invokes the rule directly via the JUnit
     * Jupiter engine, proven to execute under Surefire, matching
     * {@link #shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild}'s exact pattern. */
    @Test
    void shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild() {
        shouldPreventCrossModuleEntityImports.check(analyzedClasses());
    }

    /** T35: same canary pattern for the controller-to-service rule. */
    @Test
    void controllersDependOnlyOnTheirOwnModuleServicesIsCheckedDuringStandardBuild() {
        controllersDependOnlyOnTheirOwnModuleServices.check(analyzedClasses());
    }
}
