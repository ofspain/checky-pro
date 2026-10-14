package com.themistra.payment;

import com.themistra.payment.archtestfixtures.RogueFloatingPointMoneyUser;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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
            Set.of("float", "double", "java.lang.Float", "java.lang.Double");

    /**
     * L6: money is {@code NUMERIC}/{@code BigDecimal} only. Scans fields, method return types,
     * and method parameters (Phase 3 finding 6 — a parameter-only scope, like a fields-and-
     * returns-only scope, would miss a future DTO constructor or helper that merely *accepts* a
     * floating-point amount). A custom {@link ArchCondition}, not the built-in
     * {@code haveRawType}/{@code haveRawReturnType} DSL, because no built-in ArchUnit predicate
     * covers "any parameter has a forbidden type" cleanly — one condition keeps all three checks
     * consistent and keeps this rule a single named test, per {@code package.md} §8.
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
                }
            })
            .because("L6: money is NUMERIC/BigDecimal only - no float/double/Float/Double anywhere");

    private static JavaClasses analyzedClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ANALYZED_PACKAGE);
    }

    @Test
    void shouldRejectFloatingPointMoneyTypesAtArchUnit() {
        shouldRejectFloatingPointMoneyTypes.check(analyzedClasses());
    }

    /** The canary above only proves the rule passes on already-clean code - it says nothing
     * about whether it can ever actually fail. {@link RogueFloatingPointMoneyUser} deliberately
     * violates all three checked shapes (field, method return type, method parameter) in one
     * class, so this test exercises all three violation paths at once. */
    @Test
    void shouldRejectFloatingPointMoneyTypesAtArchUnitActuallyFailsAgainstAGenuineViolation() {
        JavaClasses violatingClasses = new ClassFileImporter().importClasses(RogueFloatingPointMoneyUser.class);

        assertThatThrownBy(() -> shouldRejectFloatingPointMoneyTypes.check(violatingClasses))
                .as("a float field, a float-returning method, and a Double parameter must each fail this rule")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("field")
                .hasMessageContaining("amount")
                .hasMessageContaining("forbidden floating-point type double")
                .hasMessageContaining("getFloatAmount")
                .hasMessageContaining("acceptDoubleAmount");
    }
}
