package com.themistra.crypto;

import archtestfixtures.RogueAlwaysTrueController;
import archtestfixtures.RogueClassGuardedController;
import archtestfixtures.RogueControllerDependent;
import archtestfixtures.RogueOpenController;
import archtestfixtures.RogueRepositoryController;
import archtestfixtures.RogueUnguardedController;
import com.themistra.crypto.common.PublicEndpoints;
import com.themistra.crypto.common.ResourceServerConfig;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FU-1 and FU-2, ported from {@code services/payment}'s {@code ArchitectureTest} (open-issues
 * sweep, 2026-10-20).
 *
 * <ul>
 *   <li>FU-2: every request handler states its access rule with {@code @PreAuthorize}, on the
 *       method or its class. Public handlers say {@code permitAll()}, and that marker is tied to
 *       {@code PublicEndpoints} so it cannot be used to open a new path quietly.</li>
 *   <li>FU-1: a controller goes through a service, never straight to a repository or the
 *       database, and nothing depends back up on a controller.</li>
 *   <li>Why FU-1 is written this way (Kimi sweep review, Finding 8, 2026-10-20): {@code agents.md}
 *       asks for {@code api -> application -> domain}, but no module has those packages. The rules
 *       therefore key on {@code @RestController} and the persistence types, not on package names.
 *       If modules are later split into such packages, add a package-based rule next to these;
 *       do not replace them.</li>
 * </ul>
 *
 * <p>Plain {@code @Test} methods only: {@code @ArchTest} fields do not run under this repo's
 * Surefire setup, so each rule is enforced by the canary that checks it.</p>
 */
class ControllerRulesArchitectureTest {

    private static final String ANALYZED_PACKAGE = "com.themistra.crypto";

    private static final JavaClasses analyzedClasses = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(ANALYZED_PACKAGE);

    private static final ArchRule shouldRequirePreAuthorizeOnEveryControllerHandler = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .should(new ArchCondition<JavaMethod>("carry @PreAuthorize on the handler or its class") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    if (isHandler(method) && !method.isAnnotatedWith(PreAuthorize.class)
                            && !method.getOwner().isAnnotatedWith(PreAuthorize.class)) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " is a request handler without @PreAuthorize"));
                    }
                }
            })
            .because("FU-2: every endpoint states the access it requires");

    private static final ArchRule shouldKeepControllersAwayFromPersistence = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAssignableTo(Repository.class)
            .orShould().dependOnClassesThat().areAssignableTo("jakarta.persistence.EntityManager")
            .orShould().dependOnClassesThat().areAssignableTo(JdbcTemplate.class)
            .because("FU-1 layering: a controller calls a service, never the persistence layer");

    private static final ArchRule shouldNotDependOnControllers = noClasses()
            .that().areNotAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAnnotatedWith(RestController.class)
            .because("FU-1 layering: dependencies point from the controllers down, never back up");

    private static boolean isHandler(JavaMethod method) {
        return method.isAnnotatedWith(RequestMapping.class) || method.isMetaAnnotatedWith(RequestMapping.class);
    }

    /** Kimi sweep review, Finding 2 (2026-10-20): the only access expressions a handler here may
     * use. An expression outside this set, such as an always-true {@code "true"}, fails
     * {@link #everyHandlerUsesAKnownAccessExpression}, so the public check below can match
     * {@code permitAll()} exactly instead of trusting a substring. */
    private static final Set<String> KNOWN_ACCESS_EXPRESSIONS = Set.of(
            "permitAll()", "hasAuthority('" + ResourceServerConfig.INTERNAL_SCOPE_AUTHORITY + "')");

    /** Every handler in {@code classes} with its effective {@code @PreAuthorize} expression (the
     * method's, else its class's), keyed {@code "METHOD /class-path/method-path"}. */
    private static Map<String, String> handlerAccess(JavaClasses classes) {
        Map<String, String> handlers = new LinkedHashMap<>();
        for (JavaClass javaClass : classes) {
            if (!javaClass.isAnnotatedWith(RestController.class)) {
                continue;
            }
            Class<?> type = javaClass.reflect();
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
            String prefix = classMapping == null || classMapping.path().length == 0 ? "" : classMapping.path()[0];
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                PreAuthorize access = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
                if (access == null) {
                    access = AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class);
                }
                String path = prefix + (mapping.path().length == 0 ? "" : mapping.path()[0]);
                assertThat(mapping.method()).as("%s maps exactly one HTTP method", method).hasSize(1);
                handlers.put(mapping.method()[0].name() + " " + path, access == null ? null : access.value());
            }
        }
        return handlers;
    }

    /** Every {@code permitAll()} handler in {@code classes}, as {@code "METHOD /path"}. */
    private static Set<String> permitAllHandlers(JavaClasses classes) {
        Set<String> handlers = new HashSet<>();
        handlerAccess(classes).forEach((handler, expression) -> {
            if ("permitAll()".equals(expression)) {
                handlers.add(handler);
            }
        });
        return handlers;
    }

    /** Every {@code PublicEndpoints.PATTERNS} entry that a controller serves (the actuator paths
     * are served by Spring Boot, not a controller). The patterns are public for any HTTP method, so
     * they are compared as paths (Kimi sweep review, Finding 3, 2026-10-20). */
    private static Set<String> allowlistedPublicPaths() {
        return Arrays.stream(PublicEndpoints.PATTERNS)
                .filter(pattern -> !pattern.startsWith("/actuator/"))
                .collect(Collectors.toSet());
    }

    private static Set<String> pathsOf(Set<String> handlers) {
        return handlers.stream().map(handler -> handler.substring(handler.indexOf(' ') + 1))
                .collect(Collectors.toSet());
    }

    @Test
    void shouldRequirePreAuthorizeOnEveryControllerHandlerIsCheckedDuringStandardBuild() {
        shouldRequirePreAuthorizeOnEveryControllerHandler.check(analyzedClasses);
    }

    /** The canary passes on clean code; this proves the rule can fail. The unguarded handler is
     * reported, while a method-level and a class-level {@code @PreAuthorize} are both accepted. */
    @Test
    void shouldRequirePreAuthorizeOnEveryControllerHandlerActuallyFailsAgainstAGenuineViolation() {
        JavaClasses violating = new ClassFileImporter().importClasses(RogueUnguardedController.class);
        assertThatThrownBy(() -> shouldRequirePreAuthorizeOnEveryControllerHandler.check(violating))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RogueUnguardedController.unguarded()")
                .hasMessageNotContaining("RogueUnguardedController.guarded()");

        shouldRequirePreAuthorizeOnEveryControllerHandler.check(
                new ClassFileImporter().importClasses(RogueClassGuardedController.class));
    }

    /** Guards against a vacuous pass: the real controllers are analyzed and have handlers. */
    @Test
    void shouldRequirePreAuthorizeOnEveryControllerHandlerSeesTheRealHandlers() {
        long handlers = analyzedClasses.stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(RestController.class))
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(ControllerRulesArchitectureTest::isHandler)
                .count();
        assertThat(handlers).as("AttestController, WatchController x2, VerificationKeysController").isEqualTo(4);
    }

    /** FU-2's public half: every handler marked {@code permitAll()} is a non-actuator {@code PublicEndpoints.PATTERNS}
     * entry, and every such entry has its handler. A public handler outside that list fails here. */
    @Test
    void permitAllHandlersMatchThePublicEndpointsAllowlistExactly() {
        assertThat(pathsOf(permitAllHandlers(analyzedClasses))).isEqualTo(allowlistedPublicPaths());
    }

    @Test
    void permitAllHandlersSeesAPublicHandlerOutsideTheAllowlist() {
        Set<String> rogue = permitAllHandlers(new ClassFileImporter().importClasses(RogueOpenController.class));
        assertThat(rogue).containsExactly("GET /rogue/open");
        assertThat(allowlistedPublicPaths()).doesNotContain("/rogue/open");
    }

    @Test
    void everyHandlerUsesAKnownAccessExpression() {
        Map<String, String> handlers = handlerAccess(analyzedClasses);
        assertThat(handlers).isNotEmpty();
        handlers.forEach((handler, expression) -> assertThat(expression)
                .as("%s uses an access expression outside KNOWN_ACCESS_EXPRESSIONS", handler)
                .isIn(KNOWN_ACCESS_EXPRESSIONS));
    }

    @Test
    void everyHandlerUsesAKnownAccessExpressionRejectsAnAlwaysTrueRule() {
        Map<String, String> rogue = handlerAccess(new ClassFileImporter().importClasses(RogueAlwaysTrueController.class));
        assertThat(rogue).containsEntry("GET /rogue/always-true", "true");
        assertThat(KNOWN_ACCESS_EXPRESSIONS).doesNotContain("true");
        assertThat(permitAllHandlers(new ClassFileImporter().importClasses(RogueAlwaysTrueController.class)))
                .as("an always-true rule is not counted as permitAll(), which is why the known-expression check exists")
                .isEmpty();
    }

    @Test
    void shouldKeepControllersAwayFromPersistenceIsCheckedDuringStandardBuild() {
        shouldKeepControllersAwayFromPersistence.check(analyzedClasses);
    }

    @Test
    void shouldKeepControllersAwayFromPersistenceActuallyFailsAgainstAGenuineViolation() {
        JavaClasses violating = new ClassFileImporter().importClasses(RogueRepositoryController.class);
        assertThatThrownBy(() -> shouldKeepControllersAwayFromPersistence.check(violating))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RogueRepositoryController");
    }

    @Test
    void shouldNotDependOnControllersIsCheckedDuringStandardBuild() {
        shouldNotDependOnControllers.check(analyzedClasses);
    }

    @Test
    void shouldNotDependOnControllersActuallyFailsAgainstAGenuineViolation() {
        JavaClasses violating = new ClassFileImporter()
                .importClasses(RogueControllerDependent.class, com.themistra.crypto.watch.WatchController.class);
        assertThatThrownBy(() -> shouldNotDependOnControllers.check(violating))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RogueControllerDependent");
    }
}
