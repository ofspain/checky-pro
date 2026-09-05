package com.themistra.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T38 — permanent regression guards for three of the five gap-analysis defect classes whose
 * absence was, until this task, only ever confirmed manually (`docs/architecture/gap-analysis.md`
 * §2/§3). Plain JUnit, deliberately not ArchUnit: each check here is a "this string/shape must
 * never appear" scan, not a structural bytecode analysis, and plain {@code @Test} methods are
 * proven to reliably execute under this project's Surefire setup — unlike {@code @ArchTest} rules
 * without their own canary (the still-open issue found during T32).
 *
 * <p>Plaintext credentials (AC1) and unauthenticated admin routes (AC2) already have durable,
 * pre-existing guards ({@code @ConfigurationProperties}/{@code @Validated} startup failure, and
 * {@code ArchitectureTest.shouldEnforcePublicEndpointAllowlist} respectively) and are not
 * duplicated here.</p>
 */
class GapAnalysisDefectRegressionTest {

    private static final Path MODULE_POM = Path.of("pom.xml");
    private static final Path ROOT_POM = Path.of("../../pom.xml");
    private static final Path APPLICATION_PROPERTIES = Path.of("src/main/resources/application.properties");
    private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");

    /** T38 AC3: no reference-project-style shared domain-model dependency (e.g. the reference's
     * {@code com.netra:commons-netra}) is ever introduced. Cross-service sharing in this monorepo
     * goes only through {@code contracts/} (build-time codegen, no runtime coupling) - gap-analysis
     * §2 "Shared domain-model artifact". */
    @Test
    void noSharedModelArtifactDependencyIsIntroduced() throws IOException {
        List<String> forbidden = List.of("commons-netra", "shared-domain", "shared-model");
        String modulePom = Files.readString(MODULE_POM).toLowerCase();
        String rootPom = Files.readString(ROOT_POM).toLowerCase();

        for (String needle : forbidden) {
            assertThat(modulePom).as("services/auth/pom.xml must not depend on %s", needle)
                    .doesNotContain(needle);
            assertThat(rootPom).as("root pom.xml must not depend on %s", needle)
                    .doesNotContain(needle);
        }
    }

    /** T38 AC5: {@code spring.main.allow-circular-references} is never enabled anywhere in this
     * service - gap-analysis §2: "Hides dependency cycles that later block modularization". */
    @Test
    void allowCircularReferencesIsNeverEnabled() throws IOException {
        List<String> forbidden = List.of(
                "allow-circular-references", "allowCircularReferences", "setAllowCircularReferences");

        String properties = Files.readString(APPLICATION_PROPERTIES);
        for (String needle : forbidden) {
            assertThat(properties).as("application.properties must not set %s", needle)
                    .doesNotContain(needle);
        }

        assertThat(sourceFilesContaining(forbidden))
                .as("no production Java source may reference allow-circular-references")
                .isEmpty();
    }

    /** T38 AC4: no {@code @Value} injection anywhere in production code carries a {@code :default}
     * fallback - gap-analysis §3: "misreads fail boot, not silently default". A default on
     * {@code @Value} would silently mask a missing/misconfigured property, the same failure mode
     * {@code Long.getLong} misreads produce, just via a different mechanism. */
    @Test
    void noValueAnnotationEverCarriesADefaultFallback() throws IOException {
        Pattern valueWithDefault = Pattern.compile("@Value\\(\"\\$\\{[^}]*:[^}]*}\"\\)");

        try (Stream<Path> files = Files.walk(MAIN_SOURCE_ROOT)) {
            List<Path> offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return valueWithDefault.matcher(Files.readString(path)).find();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
            assertThat(offenders)
                    .as("no @Value(\"${prop:default}\") - a missing property must fail boot, not silently default")
                    .isEmpty();
        }
    }

    private List<Path> sourceFilesContaining(List<String> needles) throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_SOURCE_ROOT)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String content = Files.readString(path);
                            return needles.stream().anyMatch(content::contains);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .toList();
        }
    }
}
