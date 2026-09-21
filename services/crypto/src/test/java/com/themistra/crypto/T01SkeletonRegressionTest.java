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
    private static final Path CRYPTO_PACKAGE_SPEC = Path.of("../../spec/crypto-service/package.md");
    private static final Path ADR_0004 = Path.of("../../docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md");

    /** AC1 (T01): SECURITY-THREAT-MODEL.md threats #1-6 name an owning task; #7-8 are untouched
     * (out of this service's scope). Originally asserted {@code "tracked"} for #1-6 - superseded by
     * T28 ("Threat-model closure"), whose entire purpose is to move each row from {@code tracked} to
     * {@code closed} once a named, passing test verifies its mitigation. Asserting {@code "closed"}
     * here is this same regression guard doing its job against the current, correct expected state,
     * not a relaxation of it. Renamed (T28 Phase 9, Kimi Phase 8 Finding #6) to match: the old name's
     * "Tracks" verb described the pre-T28 state this test no longer asserts. */
    @Test
    void threatModelClosesThreatsOneToSixWithAnOwningTaskAndLeavesSevenEightUntouched() throws IOException {
        String[] lines = Files.readString(THREAT_MODEL).split("\n");

        // T28 Phase 9 (Kimi Phase 8 Findings #1/#2): "closed" alone would also match a row reading
        // "closed - no test cited", and wouldn't notice row #3/#4/#5's own documented caveats being
        // silently dropped by a future edit. A real ClassName.methodName citation, plus each row's
        // own caveat keyword, must both be present.
        for (int n = 1; n <= 6; n++) {
            String row = rowStartingWith(lines, "| " + n + " |");
            assertThat(row).as("threat #%d row", n).contains("closed");
            assertThat(row).as("threat #%d must cite a real ClassName.methodName test, not just say "
                    + "\"closed\"", n).containsPattern("`[A-Za-z0-9]+\\.[a-zA-Z0-9]+`");
            assertThat(row.trim()).as("threat #%d must name an owning task, not be left empty", n)
                    .doesNotEndWith("| — |");
        }
        String row3 = rowStartingWith(lines, "| 3 |");
        assertThat(row3).as("threat #3's pre-existing-defect caveat must not be silently dropped")
                .contains("currently fails");
        String row4 = rowStartingWith(lines, "| 4 |");
        assertThat(row4).as("threat #4's code-path-only caveat must not be silently dropped")
                .contains("code-path only");
        String row5 = rowStartingWith(lines, "| 5 |");
        assertThat(row5).as("threat #5's crypto-service-scope caveat must not be silently dropped")
                .contains("crypto-service portion");
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

    /** T29 (Kimi Phase 3 Finding #8): guards the one thing T29 actually changes -
     * {@code package.md}'s own header - against a silent revert. Deliberately narrow: does not assert
     * every Section 9 item is {@code [x]}, since item 13 (mvn verify / Docker build) is genuinely,
     * honestly partial as of T29 and a broader assertion would either be wrong today or need constant
     * updating as its own follow-up lands. */
    @Test
    void packageSpecHeaderReflectsReadyForImplAndVersionZeroTwo() throws IOException {
        String spec = Files.readString(CRYPTO_PACKAGE_SPEC);

        assertThat(spec).as("Version must be bumped to 0.2").contains("| Version | `0.2` |");
        assertThat(spec).as("Status must be READY FOR IMPL").contains("| Status | `READY FOR IMPL` |");
    }

    /** T29 Phase 9 (Kimi Phase 8 Finding #6), strengthened at Phase 11 (Kimi Finding #2): a silent
     * revert of Q1/Q2/Q3/Q7's own resolution notes would not be caught by the header-only guard above.
     * Reuses {@link #rowStartingWith} - it is a generic "line starting with this prefix" scan, equally
     * valid for a {@code package.md} §11 bullet as for a {@code SECURITY-THREAT-MODEL.md} table row.
     * Q8 already carried its own, differently-dated resolution before T29 and is deliberately excluded
     * from both loops. The Q4/Q5/Q6 assertion checks for any {@code "Resolved ("} at all, not just
     * today's date - Phase 9's own original date-scoped check would have silently passed a future
     * resolution dated any other day, which would still mean those questions are no longer open. */
    @Test
    void resolvedOpenQuestionsCarryTheirResolutionNoteAndUnresolvedOnesDoNotClaimThisTasksResolution()
            throws IOException {
        String[] lines = Files.readString(CRYPTO_PACKAGE_SPEC).split("\n");

        for (int n : new int[] {1, 2, 3, 7}) {
            String line = rowStartingWith(lines, "- Q" + n + ".");
            assertThat(line).as("Q%d must carry T29's resolution note", n).contains("Resolved (2026-09-21");
        }
        for (int n : new int[] {4, 5, 6}) {
            String line = rowStartingWith(lines, "- Q" + n + ".");
            assertThat(line).as("Q%d must remain genuinely unresolved", n).doesNotContain("Resolved (");
        }
    }

    /** T29 Phase 9 (Kimi Phase 8 Finding #9), strengthened at Phase 11 (Kimi Finding #1): the original
     * version only asserted the honest "genuinely fails" prose survives, which a future edit could
     * satisfy while still silently ticking the checkbox itself to {@code [x]} - the prose and the
     * checkbox are two separate things a bad edit could diverge. Now asserts both on the same,
     * specific line. */
    @Test
    void item13StaysHonestlyDisclosedAsGenuinelyFailingUntilTheFollowUpLands() throws IOException {
        String[] lines = Files.readString(CRYPTO_PACKAGE_SPEC).split("\n");
        String item13 = rowStartingWith(lines, "- [ ] `mvn -pl services/crypto verify` passes");

        assertThat(item13).as("item 13 must remain unchecked until the Docker-build follow-up lands")
                .startsWith("- [ ]");
        assertThat(item13).as("item 13's genuine Docker-build failure must stay disclosed, not silently "
                        + "marked complete")
                .contains("genuinely fails");
    }

    /** T29 Phase 11 (Kimi Finding #5): the two guards above check item 13 specifically and Q1/2/3/7's
     * notes, but nothing stops items 1-12 from being silently unchecked, or a second item joining item
     * 13 as unchecked, without failing a test. Scoped to lines between the §9 and §10 headers
     * specifically (not the whole file) so an unrelated checklist added elsewhere later couldn't skew
     * the count - verified directly that no other {@code - [x]}/{@code - [ ]} line exists anywhere
     * else in this file today. */
    @Test
    void exactlyThirteenOfSectionNinesFourteenItemsAreCheckedAndOnlyItemThirteenIsNot() throws IOException {
        String[] lines = Files.readString(CRYPTO_PACKAGE_SPEC).split("\n");
        int start = indexOfLineStartingWith(lines, "## 9.");
        int end = indexOfLineStartingWith(lines, "## 10.");

        long checked = 0;
        long unchecked = 0;
        for (int i = start; i < end; i++) {
            if (lines[i].startsWith("- [x]")) {
                checked++;
            } else if (lines[i].startsWith("- [ ]")) {
                unchecked++;
            }
        }

        assertThat(checked).as("13 of Section 9's 14 items should be checked").isEqualTo(13);
        assertThat(unchecked).as("exactly 1 of Section 9's 14 items (item 13) should remain unchecked")
                .isEqualTo(1);
    }

    private static int indexOfLineStartingWith(String[] lines, String prefix) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(prefix)) {
                return i;
            }
        }
        throw new AssertionError("no line starting with \"" + prefix + "\" found in " + CRYPTO_PACKAGE_SPEC);
    }
}
