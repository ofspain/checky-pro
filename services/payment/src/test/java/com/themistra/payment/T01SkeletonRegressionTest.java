package com.themistra.payment;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T01 — permanent regression guards for this task's own acceptance criteria (AC1-AC5) plus the
 * three additions the frozen brief folded in from Phase 3 (version alignment including
 * flyway-maven-plugin, a real S3Client construction, and an L13 negative-proof), which would
 * otherwise only be checked by file reads during the review phases and would not fail a later
 * build if silently reverted. Plain JUnit, deliberately not ArchUnit (mirrors
 * {@code crypto-service}/{@code notification-service}'s own identical T01 convention) - there is
 * no production code yet for a structural rule to analyze.
 */
class T01SkeletonRegressionTest {

    private static final Path MODULE_POM = Path.of("pom.xml");
    private static final Path ROOT_POM = Path.of("../../pom.xml");
    private static final Path AUTH_POM = Path.of("../auth/pom.xml");
    private static final Path DOCKERFILE = Path.of("Dockerfile");
    private static final Path APPLICATION_SOURCE =
            Path.of("src/main/java/com/themistra/payment/PaymentServiceApplication.java");

    /** AC1: services/payment is registered in the root reactor, after the three existing
     * services, with the dependency-order comment this task's own pom edit left intact. */
    @Test
    void rootPomRegistersPaymentServiceAfterTheThreeExistingServicesWithOrderingComment() throws IOException {
        String rootPom = Files.readString(ROOT_POM);

        assertThat(rootPom).contains("<module>services/auth</module>");
        assertThat(rootPom).contains("<module>services/crypto</module>");
        assertThat(rootPom).contains("<module>services/notification</module>");
        assertThat(rootPom).contains("<module>services/payment</module>");
        assertThat(rootPom.indexOf("<module>services/notification</module>"))
                .as("services/payment must be listed after the three existing services (dependency order)")
                .isLessThan(rootPom.indexOf("<module>services/payment</module>"));
        assertThat(rootPom).as("module ordering must be explained, not just followed")
                .contains("dependency order");
    }

    /** AC2: the module pom declares exactly the foundation dependency set, with the identifiers
     * and version pins the frozen brief names. */
    @Test
    void paymentPomDeclaresTheExactFoundationDependencySet() throws IOException {
        String pom = Files.readString(MODULE_POM);

        assertThat(pom).contains("<artifactId>payment-service</artifactId>");
        assertThat(pom).contains("<relativePath>../../pom.xml</relativePath>");
        assertThat(dependencyVersion(pom, "bom")).isEqualTo("2.50.2");
        assertThat(propertyValue(pom, "testcontainers.version")).isEqualTo("1.21.4");

        // Web + validation - "exactly once" (Phase 11 finding 3), not "contains", so an
        // accidentally duplicated <dependency> block would fail this suite rather than pass
        // silently (Maven itself tolerates a duplicate without complaint).
        assertDeclaredExactlyOnce(pom, "<artifactId>spring-boot-starter-web</artifactId>");
        assertDeclaredExactlyOnce(pom, "<artifactId>spring-boot-starter-validation</artifactId>");

        // Resource server only
        assertDeclaredExactlyOnce(pom, "<artifactId>spring-boot-starter-oauth2-resource-server</artifactId>");
        assertDeclaredExactlyOnce(pom, "<artifactId>spring-security-oauth2-resource-server</artifactId>");

        // Persistence
        assertDeclaredExactlyOnce(pom, "<artifactId>spring-boot-starter-data-jpa</artifactId>");
        assertDeclaredExactlyOnce(pom, "<artifactId>flyway-core</artifactId>");
        assertDeclaredExactlyOnce(pom, "<artifactId>flyway-database-postgresql</artifactId>");
        // The bare "postgresql" artifactId is NOT exactly-once: the ordinary JDBC driver
        // (org.postgresql:postgresql) and Testcontainers' own module (org.testcontainers:postgresql)
        // legitimately share that exact tag string - asserted as exactly two occurrences, and the
        // Testcontainers one specifically pinned by its groupId pairing right below (Phase 11
        // finding 1 - a plain "contains" here would stay green even if the Testcontainers module
        // were deleted, since the JDBC driver's own identical tag would still match).
        long postgresqlTags = Pattern.compile("<artifactId>postgresql</artifactId>").matcher(pom).results().count();
        assertThat(postgresqlTags).as("JDBC driver + Testcontainers postgresql, exactly two").isEqualTo(2);
        assertThat(pom).containsPattern("<groupId>org\\.testcontainers</groupId>\\s*<artifactId>postgresql</artifactId>");

        // Kafka
        assertDeclaredExactlyOnce(pom, "<artifactId>spring-kafka</artifactId>");
        assertDeclaredExactlyOnce(pom, "<artifactId>kafka</artifactId>");

        // Receipt store (L9)
        assertDeclaredExactlyOnce(pom, "<artifactId>s3</artifactId>");

        // Observability
        assertDeclaredExactlyOnce(pom, "<artifactId>spring-boot-starter-actuator</artifactId>");
        assertDeclaredExactlyOnce(pom, "<artifactId>micrometer-registry-prometheus</artifactId>");

        // Test scope
        assertDeclaredExactlyOnce(pom, "<artifactId>spring-boot-starter-test</artifactId>");
        assertDeclaredExactlyOnce(pom, "<artifactId>spring-security-test</artifactId>");
        assertDeclaredExactlyOnce(pom, "<artifactId>spring-boot-testcontainers</artifactId>");
        assertDeclaredExactlyOnce(pom, "<artifactId>junit-jupiter</artifactId>");
        assertDeclaredExactlyOnce(pom, "<artifactId>archunit-junit5</artifactId>");
        assertDeclaredExactlyOnce(pom, "<artifactId>awaitility</artifactId>");
        assertThat(dependencyVersion(pom, "archunit-junit5")).isEqualTo("1.3.0");
        assertThat(dependencyVersion(pom, "awaitility")).isEqualTo("4.2.2");
    }

    /** AC3: this task deliberately excludes the KMS SDK (L3), any chain client, the OIDC-issuer
     * starter (auth-only), and every dependency deferred to a later task (ShedLock, the YAML
     * contract-test parser, and auth's own rate-limiting library). Checked against the pom with
     * its own XML comments stripped first - this pom's comments explain each exclusion by naming
     * the excluded coordinate verbatim (for a human reader), which would otherwise defeat a plain
     * substring negative-proof. */
    @Test
    void paymentPomExcludesKmsChainClientsAndDeferredDependencies() throws IOException {
        String pom = stripXmlComments(Files.readString(MODULE_POM));

        assertThat(pom).as("L3: no KMS SDK").doesNotContain("<artifactId>kms</artifactId>");
        assertThat(pom).as("L3: no chain client").doesNotContain("org.web3j");
        assertThat(pom).doesNotContain("io.github.tronprotocol");
        assertThat(pom).as("payment-service validates tokens, it never issues them")
                .doesNotContain("oauth2-authorization-server");
        assertThat(pom).as("T08: ShedLock now expected (InvoiceExpirySweepJob)")
                .contains("net.javacrumbs.shedlock");
        assertThat(pom).as("T24: jackson-dataformat-yaml now expected (PaymentsOpenApiContractTest)")
                .contains("jackson-dataformat-yaml");
        assertThat(pom).as("bucket4j is auth-only rate limiting, never used here")
                .doesNotContain("com.bucket4j");
    }

    /** AC4: the Application class carries only the annotations the tasks landed so far actually
     * introduced, and the standard main/SpringApplication.run entry point, nothing else. A
     * structural source-text check, not a Maven shell-out (Phase 3 finding 5, T01). The regex/
     * count checks below run against the comment-stripped source (Phase 11 finding 2, T01) - this
     * same file already proved once, for the pom's own checks, that a comment explaining *why*
     * something is present can accidentally contain the literal text a count-based check scans
     * for; nothing exempts this, the one Java-source check in the suite, from that same risk.
     * <b>Updated at T03</b> (mirrors {@code notification-service}'s own identical T01-test
     * evolution): {@code @ConfigurationPropertiesScan} is now expected, since T03 introduced this
     * service's first {@code @ConfigurationProperties} classes - the count below moved from 1 to
     * 2, not left stale against the T01-only shape.
     * <b>Updated at T04</b>: {@code @EnableScheduling} is now expected, since T04 introduced this
     * service's first {@code @Scheduled} method ({@link com.themistra.payment.events.OutboxRelay}) -
     * the count below moved from 2 to 3, for the same reason.
     * <b>Updated at T08</b>: {@code @EnableSchedulerLock} is now expected, since T08 introduced
     * this service's first ShedLock-coordinated job
     * ({@link com.themistra.payment.invoice.InvoiceExpirySweepJob}) - the count below moved from
     * 3 to 4, for the same reason. */
    @Test
    void applicationClassIsBareWithStandardMainMethod() throws IOException {
        String source = Files.readString(APPLICATION_SOURCE);

        assertThat(source).contains("@SpringBootApplication");
        assertThat(source).as("T03 added the first @ConfigurationProperties classes to scan")
                .contains("@ConfigurationPropertiesScan");
        assertThat(source).as("T04 added the first @Scheduled method (OutboxRelay)")
                .contains("@EnableScheduling");
        assertThat(source).as("T08 added the first ShedLock-coordinated job (InvoiceExpirySweepJob)")
                .contains("@EnableSchedulerLock");
        assertThat(source)
                .as("SpringApplication.run must be called with the real class and args")
                .contains("SpringApplication.run(PaymentServiceApplication.class, args);");

        String strippedSource = stripJavaComments(source);

        // File-wide, not strictly position-scoped to "immediately before the class declaration"
        // (Phase 11 finding 4, T01, no test change needed for it then - documented there instead):
        // today there are exactly four @-starting lines (@SpringBootApplication,
        // @ConfigurationPropertiesScan, @EnableScheduling, @EnableSchedulerLock) and all four are
        // class-level, so this remains equivalent in practice. A future task adding an annotated
        // method to this same class would need to revisit this assertion's scope, not treat a
        // failure here as a regression.
        long classLevelAnnotations = strippedSource.lines()
                .filter(line -> line.trim().startsWith("@"))
                .count();
        assertThat(classLevelAnnotations)
                .as("no annotation beyond @SpringBootApplication/@ConfigurationPropertiesScan/"
                        + "@EnableScheduling/@EnableSchedulerLock - none else is added until the "
                        + "task that introduces what it enables")
                .isEqualTo(4);

        long methodCount = Pattern.compile("\\bpublic\\s+static\\s+void\\s+main\\b").matcher(strippedSource).results().count();
        assertThat(methodCount).as("exactly one main method, no other method").isEqualTo(1);
    }

    /** AC5: the build plugins and finalName this task's own frozen brief names. */
    @Test
    void buildPluginsAndFinalNameAreRegistered() throws IOException {
        String pom = Files.readString(MODULE_POM);

        assertDeclaredExactlyOnce(pom, "<artifactId>spring-boot-maven-plugin</artifactId>");
        assertDeclaredExactlyOnce(pom, "<artifactId>flyway-maven-plugin</artifactId>");
        assertThat(dependencyVersion(pom, "flyway-maven-plugin")).isEqualTo("11.7.2");
        assertThat(pom).contains("<schemas>payments</schemas>");
        assertThat(stripXmlComments(pom))
                .as("the Flyway plugin is never bound to an execution - local-dev only "
                        + "(stripped of this pom's own explanatory comment, which names the tag "
                        + "verbatim for a human reader)")
                .doesNotContain("<executions>");
        assertThat(pom).contains("<finalName>payment-service</finalName>");
    }

    /** Shared dependency *versions* this pom does carry must not silently drift from
     * services/auth/pom.xml's own values (Phase 3 finding 2, extended by finding 9 to include the
     * Flyway plugin version). Deliberately omits shedlock-spring, unlike crypto's own version of
     * this test - that dependency is not present in this task's pom at all (see the exclusion
     * test above). */
    @Test
    void sharedDependencyVersionsStayAlignedWithAuthService() throws IOException {
        String paymentPom = Files.readString(MODULE_POM);
        String authPom = Files.readString(AUTH_POM);

        assertThat(propertyValue(paymentPom, "testcontainers.version"))
                .isEqualTo(propertyValue(authPom, "testcontainers.version"));
        assertThat(dependencyVersion(paymentPom, "archunit-junit5"))
                .isEqualTo(dependencyVersion(authPom, "archunit-junit5"));
        assertThat(dependencyVersion(paymentPom, "awaitility"))
                .isEqualTo(dependencyVersion(authPom, "awaitility"));
        assertThat(dependencyVersion(paymentPom, "bom"))
                .as("AWS SDK BOM version (dependencyManagement)")
                .isEqualTo(dependencyVersion(authPom, "bom"));
        assertThat(dependencyVersion(paymentPom, "flyway-maven-plugin"))
                .isEqualTo(dependencyVersion(authPom, "flyway-maven-plugin"));
    }

    /** Proves the AWS SDK BOM + s3 dependency actually resolve a constructible client now (Phase
     * 3 finding 8, mirrors notification-service's sesV2ClientCanActuallyBeConstructed) - dummy
     * static credentials, no network call, no real AWS account needed. */
    @Test
    void s3ClientCanActuallyBeConstructed() {
        try (S3Client client = S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build()) {
            assertThat(client).isNotNull();
        }
    }

    /** L13 negative-proof: the only credential-shaped string in either pom is the one documented,
     * already-existing, cross-service carve-out (the Flyway plugin's local-dev-only placeholder
     * password) - never anything else, and never more than the one expected occurrence. */
    @Test
    void noSecretOrCredentialStringOutsideTheDocumentedFlywayLocalDevCarveOut() throws IOException {
        String pom = Files.readString(MODULE_POM);

        long passwordTags = pom.lines().filter(line -> line.contains("<password>")).count();
        assertThat(passwordTags)
                .as("exactly one <password> tag - the documented Flyway local-dev carve-out")
                .isEqualTo(1);
        assertThat(pom).contains("<password>checky-local-only</password>");

        String strippedPom = stripXmlComments(pom);
        assertThat(strippedPom).doesNotContainPattern("(?i)<secret>|<apiKey>|<accessKey>|<accessToken>");

        String rootPom = stripXmlComments(Files.readString(ROOT_POM));
        assertThat(rootPom).doesNotContainPattern("(?i)<password>|<secret>|<apiKey>|<accessKey>");
    }

    /** T27 (frozen brief D4, Phase 3 F4). A permanent guard on {@code services/payment/Dockerfile}.
     * The {@code agents.md} Deployment rule and L13 would otherwise be checked only once, by hand,
     * at T27, and a later edit could silently break them.
     *
     * <p>The module-pom check reads the root pom's own {@code <module>} list rather than
     * hard-coding four names. So adding a module fails this test until its pom is copied. That is
     * the bug that broke the auth, crypto and notification Dockerfiles (follow-up FU-T27-1). */
    @Test
    void paymentDockerfileHonoursBuildContextAndSecurityRules() throws IOException {
        List<String> lines = stripDockerfileComments(Files.readString(DOCKERFILE));

        List<String> fromLines = lines.stream().filter(line -> line.startsWith("FROM ")).toList();
        assertThat(fromLines).as("multi-stage build").hasSizeGreaterThanOrEqualTo(2);
        String finalFrom = fromLines.get(fromLines.size() - 1);
        assertThat(finalFrom).as("the runtime stage is distroless JRE 21, non-root (agents.md Deployment)")
                .isEqualTo("FROM gcr.io/distroless/java21-debian12:nonroot");
        List<String> runtimeStage = lines.subList(lines.lastIndexOf(finalFrom), lines.size());
        assertThat(runtimeStage).as("the runtime stage runs as nonroot").contains("USER nonroot");
        assertThat(runtimeStage.stream().filter(line -> line.startsWith("COPY --from=")).toList())
                .as("the runtime stage takes only the repackaged jar from the build stage")
                .containsExactly("COPY --from=build /workspace/services/payment/target/payment-service.jar app.jar");
        assertThat(runtimeStage).as("exec-form ENTRYPOINT: distroless has no shell to run a shell-form one")
                .contains("ENTRYPOINT [\"java\", \"-XX:MaxRAMPercentage=75.0\", \"-jar\", \"app.jar\"]");

        assertThat(lines).as("ADD is never used (it can fetch URLs and unpack archives)")
                .noneMatch(line -> line.startsWith("ADD "));
        assertThat(lines).as("only explicit paths are copied, never the whole context")
                .noneMatch(T01SkeletonRegressionTest::copiesWholeContext);
        assertThat(lines).as("L13: no ENV or ARG instruction, so no value can be baked into a layer "
                        + "or the image history")
                .noneMatch(line -> line.startsWith("ENV ") || line.startsWith("ARG "));

        assertThat(lines).as("the root pom is in the build context").contains("COPY pom.xml ./");
        Matcher modules = Pattern.compile("<module>([^<]+)</module>")
                .matcher(stripXmlComments(Files.readString(ROOT_POM)));
        List<String> declaredModules = new ArrayList<>();
        while (modules.find()) {
            declaredModules.add(modules.group(1).trim());
        }
        assertThat(declaredModules).as("the root pom's module list was read").isNotEmpty();
        for (String module : declaredModules) {
            assertThat(lines).as("Maven validates every reactor module even with -pl, so %s/pom.xml "
                            + "must be in the build context", module)
                    .contains("COPY " + module + "/pom.xml " + module + "/");
        }

        assertThat(lines).as("server.port default (application.properties)").contains("EXPOSE 8083");
    }

    /** Drops Dockerfile comment lines and blank lines, trims the rest, and upper-cases each
     * instruction keyword. Docker reads keywords case-insensitively, so {@code env X=1} must not
     * slip past a check for {@code ENV}. The header comment explains the rules this test checks,
     * and may name the very instructions it bans, which is why comments are dropped. */
    private static List<String> stripDockerfileComments(String dockerfile) {
        return dockerfile.lines().map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> {
                    String[] keywordAndRest = line.split("\\s+", 2);
                    return keywordAndRest[0].toUpperCase(Locale.ROOT)
                            + (keywordAndRest.length > 1 ? " " + keywordAndRest[1] : "");
                })
                .toList();
    }

    /** True for a COPY whose source is the whole build context ({@code .} or {@code ./}), with
     * any {@code --flag} options such as {@code --chown} skipped. The last argument is the
     * destination, not a source. */
    private static boolean copiesWholeContext(String line) {
        if (!line.startsWith("COPY ")) {
            return false;
        }
        List<String> args = Arrays.stream(line.substring(5).trim().split("\\s+"))
                .filter(arg -> !arg.startsWith("--")).toList();
        return args.subList(0, Math.max(0, args.size() - 1)).stream()
                .anyMatch(source -> source.equals(".") || source.equals("./"));
    }

    /** Strips XML comments before a negative-proof substring/pattern check. Both this pom and the
     * root pom explain each decision in a comment that may legitimately name the very coordinate
     * or tag the check elsewhere asserts is absent from real content - a plain, uncommented
     * substring search cannot tell the two apart. */
    private static String stripXmlComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?-->", "");
    }

    /** Java-source counterpart to {@link #stripXmlComments} (Phase 11 finding 2): strips block
     * and line comments before a regex/count-based check on the real code. */
    private static String stripJavaComments(String java) {
        return java.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//.*", "");
    }

    /** Asserts a dependency coordinate appears exactly once - "at least once" (plain
     * {@code contains}) would not catch an accidentally duplicated {@code <dependency>} block
     * (Phase 11 finding 3). */
    private static void assertDeclaredExactlyOnce(String pom, String artifactIdTag) {
        long occurrences = Pattern.compile(Pattern.quote(artifactIdTag)).matcher(pom).results().count();
        assertThat(occurrences).as("%s must appear exactly once, not duplicated", artifactIdTag).isEqualTo(1);
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
