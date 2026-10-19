package com.themistra.payment.common;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** T23 (R29): proves the catalogue's own structural guarantees - every constant is a distinct,
 * well-formed, absolute URI sharing one base, and the class cannot be ordinarily instantiated.
 * Reflection-based, mirroring this session's own established "a reflection check on a pure-
 * constants class" precedent rather than needing a Spring context (no such context is needed for
 * a class with no bean/wiring behavior). */
class ProblemTypesTest {

    /** Every field in the class is a distinct, well-formed, absolute URI — no two constants
     * accidentally share the same catalogue value (a copy-paste typo in the kebab-case suffix
     * would otherwise go unnoticed). {@code VALIDATION_ERROR} and {@code FORBIDDEN} are each
     * referenced by two different call sites in production code (Phase 2/4) — that sharing
     * happens at the call-site level, not here: there is no legitimate case where two of these
     * fields hold the same {@link URI} value. */
    @Test
    void everyConstantIsADistinctAbsoluteUri() throws Exception {
        Map<URI, List<String>> namesByUri = new HashMap<>();
        for (Field field : ProblemTypes.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != URI.class) {
                continue;
            }
            URI value = (URI) field.get(null);
            assertThat(value).as("constant %s must not be null", field.getName()).isNotNull();
            assertThat(value.isAbsolute()).as("constant %s must be an absolute URI", field.getName()).isTrue();
            namesByUri.computeIfAbsent(value, v -> new ArrayList<>()).add(field.getName());
        }

        assertThat(namesByUri).as("ProblemTypes must declare at least one constant").hasSizeGreaterThanOrEqualTo(11);
        namesByUri.forEach((uri, names) -> assertThat(names)
                .as("constant value %s must not be reused by more than one field", uri)
                .hasSize(1));
    }

    @Test
    void everyConstantSharesTheSameBase() throws Exception {
        for (Field field : ProblemTypes.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != URI.class) {
                continue;
            }
            URI value = (URI) field.get(null);
            assertThat(value.toString())
                    .as("constant %s must share the catalogue's single base", field.getName())
                    .startsWith("https://checky.pro/problems/");
        }
    }

    /** {@code private} is a compile-time access guard, not something reflection can meaningfully
     * prove throws at runtime (a reflective caller that calls {@code setAccessible(true)} can
     * always construct it regardless) - the real guarantee this class offers is "no ordinary
     * caller can construct it," which this asserts directly via the modifier itself, mirroring
     * {@code PublicEndpoints}' own identical {@code private} no-arg constructor shape (no
     * existing test for that one either - this is the first such test in this codebase). */
    @Test
    void hasAPrivateNoArgConstructor() throws Exception {
        Constructor<ProblemTypes> constructor = ProblemTypes.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }

    @Test
    void isAFinalClass() {
        assertThat(Modifier.isFinal(ProblemTypes.class.getModifiers())).isTrue();
    }
}
