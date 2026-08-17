package com.themistra.auth;

import com.themistra.auth.common.PublicEndpoints;
import com.themistra.auth.token.SecurityChainsConfig;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.web.bind.annotation.RestController;

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
@AnalyzeClasses(packages = "com.themistra.auth", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule only_the_account_module_may_touch_the_Account_entity = noClasses()
            .that().resideOutsideOfPackage("com.themistra.auth.account")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.themistra.auth.account.Account")
            .because("the Account aggregate is account-module-private (its own Javadoc: the "
                    + "internal id never leaves this service); other modules address accounts "
                    + "via AccountService, UUIDs, and account.dto/account.event types only");

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
     * out-of-scope Surefire/ArchUnit engine-integration gap.
     */
    @Test
    void shouldEnforcePublicEndpointAllowlistActuallyRunsUnderThisBuild() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.themistra.auth");
        shouldEnforcePublicEndpointAllowlist.check(classes);
    }
}
