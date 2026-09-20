package com.themistra.crypto;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T01 — permanent regression guards for this task's own acceptance criteria (AC1-AC4), which are
 * otherwise only checked by file reads and {@code mvn validate} during the review phases and would
 * not fail a later build if silently reverted. Plain JUnit, deliberately not ArchUnit, matching
 * {@code GapAnalysisDefectRegressionTest}'s (auth-service T38) established style: a "this content
 * must be present/absent" scan, not a structural bytecode analysis - there is no production code
 * yet for a structural rule to analyze.
 */
class T01SkeletonRegressionTest {

    private static final Path MODULE_POM = Path.of("pom.xml");
    private static final Path ROOT_POM = Path.of("../../pom.xml");
    private static final Path AUTH_POM = Path.of("../auth/pom.xml");
    private static final Path APPLICATION_PROPERTIES = Path.of("src/main/resources/application.properties");
    private static final Path THREAT_MODEL = Path.of("../../SECURITY-THREAT-MODEL.md");
    private static final Path ADR_0004 = Path.of("../../docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md");

    /** AC1: SECURITY-THREAT-MODEL.md threats #1-6 are tracked with an owning task; #7-8 are
     * untouched (out of this service's scope). */
    @Test
    void threatModelTracksThreatsOneToSixWithAnOwningTaskAndLeavesSevenEightUntouched() throws IOException {
        String[] lines = Files.readString(THREAT_MODEL).split("\n");

        for (int n = 1; n <= 6; n++) {
            String row = rowStartingWith(lines, "| " + n + " |");
            assertThat(row).as("threat #%d row", n).contains("tracked");
            assertThat(row.trim()).as("threat #%d must name an owning task, not be left empty", n)
                    .doesNotEndWith("| — |");
        }
        for (int n = 7; n <= 8; n++) {
            String row = rowStartingWith(lines, "| " + n + " |");
            assertThat(row).as("threat #%d row must remain untouched", n).contains("designed");
            assertThat(row.trim()).as("threat #%d has no owning crypto-service task", n)
                    .endsWith("| — |");
        }
    }

    private static String rowStartingWith(String[] lines, String prefix) {
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line;
            }
        }
        throw new AssertionError("no table row starting with \"" + prefix + "\" found in " + THREAT_MODEL);
    }

    /** AC2: services/crypto is registered in the root reactor, after services/auth, with the
     * dependency-order comment this task added. */
    @Test
    void rootPomRegistersCryptoServiceAfterAuthServiceWithOrderingComment() throws IOException {
        String rootPom = Files.readString(ROOT_POM);

        assertThat(rootPom).contains("<module>services/auth</module>");
        assertThat(rootPom).contains("<module>services/crypto</module>");
        assertThat(rootPom.indexOf("<module>services/auth</module>"))
                .as("services/auth must be listed before services/crypto (dependency order)")
                .isLessThan(rootPom.indexOf("<module>services/crypto</module>"));
        assertThat(rootPom).as("module ordering must be explained, not just followed")
                .contains("dependency order");
    }

    /** AC3: the chain clients and the ADR-backed KMS dependency are present; the issuer-side SAS
     * starter (auth-only, crypto is resource-server-only) is not. */
    @Test
    void cryptoPomDeclaresChainClientsAndKmsWithoutTheIssuerStarter() throws IOException {
        String pom = Files.readString(MODULE_POM);

        assertThat(pom).contains("org.web3j");
        assertThat(pom).contains("io.github.tronprotocol");
        assertThat(pom).contains("<artifactId>kms</artifactId>");
        assertThat(pom).as("crypto-service validates tokens, it never issues them")
                .doesNotContain("oauth2-authorization-server");
    }

    /** AC3: the KMS dependency's named exception (ADR-0004) actually exists and is scoped to
     * kms:Sign from the attest module only - the exact link Phase 8/9 found broken by a
     * git-staging gap, not a content defect. */
    @Test
    void adr0004ExistsAndScopesKmsSigningToTheAttestModule() throws IOException {
        assertThat(Files.exists(ADR_0004)).as("%s must exist", ADR_0004).isTrue();

        String adr = Files.readString(ADR_0004);
        assertThat(adr).contains("kms:Sign");
        assertThat(adr).contains("attest");
        assertThat(adr).contains("software.amazon.awssdk:kms");
    }

    /** AC4: Java 21 virtual threads are enabled. Not a proof that anything actually runs on one
     * (no watcher code exists yet, T09+) - a regression guard against a deleted/mistyped property. */
    @Test
    void virtualThreadsAreEnabled() throws IOException {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(APPLICATION_PROPERTIES)) {
            properties.load(in);
        }
        assertThat(properties.getProperty("spring.threads.virtual.enabled")).isEqualTo("true");
    }

    /** Dependencies intentionally pinned to match services/auth (Phase 5/6 rationale: same Spring
     * Boot version, same Testcontainers-Docker-handshake fix, same platform testing stack) do not
     * silently drift apart. */
    @Test
    void sharedDependencyVersionsStayAlignedWithAuthService() throws IOException {
        String cryptoPom = Files.readString(MODULE_POM);
        String authPom = Files.readString(AUTH_POM);

        assertThat(propertyValue(cryptoPom, "testcontainers.version"))
                .isEqualTo(propertyValue(authPom, "testcontainers.version"));
        assertThat(dependencyVersion(cryptoPom, "shedlock-spring"))
                .isEqualTo(dependencyVersion(authPom, "shedlock-spring"));
        assertThat(dependencyVersion(cryptoPom, "archunit-junit5"))
                .isEqualTo(dependencyVersion(authPom, "archunit-junit5"));
        assertThat(dependencyVersion(cryptoPom, "awaitility"))
                .isEqualTo(dependencyVersion(authPom, "awaitility"));
        assertThat(dependencyVersion(cryptoPom, "bom"))
                .as("AWS SDK BOM version (dependencyManagement)")
                .isEqualTo(dependencyVersion(authPom, "bom"));
    }

    private static String propertyValue(String pomContent, String propertyName) {
        return extractFirst(pomContent, "<" + propertyName + ">([^<]+)</" + propertyName + ">", propertyName);
    }

    private static String dependencyVersion(String pomContent, String artifactId) {
        String pattern = "<artifactId>" + Pattern.quote(artifactId) + "</artifactId>\\s*<version>([^<]+)</version>";
        return extractFirst(pomContent, pattern, artifactId);
    }

    private static String extractFirst(String content, String pattern, String label) {
        Matcher matcher = Pattern.compile(pattern).matcher(content);
        if (!matcher.find()) {
            throw new AssertionError("no match for \"" + label + "\" using pattern: " + pattern);
        }
        return matcher.group(1);
    }
}
