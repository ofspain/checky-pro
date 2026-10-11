package com.themistra.payment;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        // Web + validation
        assertThat(pom).contains("<artifactId>spring-boot-starter-web</artifactId>");
        assertThat(pom).contains("<artifactId>spring-boot-starter-validation</artifactId>");

        // Resource server only
        assertThat(pom).contains("<artifactId>spring-boot-starter-oauth2-resource-server</artifactId>");
        assertThat(pom).contains("<artifactId>spring-security-oauth2-resource-server</artifactId>");

        // Persistence
        assertThat(pom).contains("<artifactId>spring-boot-starter-data-jpa</artifactId>");
        assertThat(pom).contains("<artifactId>flyway-core</artifactId>");
        assertThat(pom).contains("<artifactId>flyway-database-postgresql</artifactId>");
        assertThat(pom).contains("<artifactId>postgresql</artifactId>");

        // Kafka
        assertThat(pom).contains("<artifactId>spring-kafka</artifactId>");

        // Receipt store (L9)
        assertThat(pom).containsPattern("<artifactId>s3</artifactId>");

        // Observability
        assertThat(pom).contains("<artifactId>spring-boot-starter-actuator</artifactId>");
        assertThat(pom).contains("<artifactId>micrometer-registry-prometheus</artifactId>");

        // Test scope
        assertThat(pom).contains("<artifactId>spring-boot-starter-test</artifactId>");
        assertThat(pom).contains("<artifactId>spring-security-test</artifactId>");
        assertThat(pom).contains("<artifactId>spring-boot-testcontainers</artifactId>");
        assertThat(pom).contains("<artifactId>junit-jupiter</artifactId>");
        assertThat(dependencyVersion(pom, "archunit-junit5")).isEqualTo("1.3.0");
        assertThat(dependencyVersion(pom, "awaitility")).isEqualTo("4.2.2");

        // Both Testcontainers modules present (postgresql, kafka) - checked separately since
        // "postgresql" also matches the main JDBC driver artifact above.
        long testcontainersPostgres = pom.lines()
                .filter(line -> line.contains("org.testcontainers") || line.contains("<artifactId>postgresql</artifactId>"))
                .count();
        assertThat(testcontainersPostgres).isGreaterThan(0);
        assertThat(pom).contains("<artifactId>kafka</artifactId>");
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
        assertThat(pom).as("ShedLock lands at task 8, not this task")
                .doesNotContain("net.javacrumbs.shedlock");
        assertThat(pom).as("jackson-dataformat-yaml lands at task 24, not this task")
                .doesNotContain("jackson-dataformat-yaml");
        assertThat(pom).as("bucket4j is auth-only rate limiting, never used here")
                .doesNotContain("com.bucket4j");
    }

    /** AC4: the Application class is bare - exactly one class-level annotation
     * (@SpringBootApplication) and the standard main/SpringApplication.run entry point, nothing
     * else. A structural source-text check, not a Maven shell-out (Phase 3 finding 5). */
    @Test
    void applicationClassIsBareWithStandardMainMethod() throws IOException {
        String source = Files.readString(APPLICATION_SOURCE);

        assertThat(source).contains("@SpringBootApplication");
        assertThat(source)
                .as("SpringApplication.run must be called with the real class and args")
                .contains("SpringApplication.run(PaymentServiceApplication.class, args);");

        long classLevelAnnotations = source.lines()
                .filter(line -> line.trim().startsWith("@"))
                .count();
        assertThat(classLevelAnnotations)
                .as("no annotation beyond @SpringBootApplication - none is added until the task "
                        + "that introduces what it enables")
                .isEqualTo(1);

        long methodCount = Pattern.compile("\\bpublic\\s+static\\s+void\\s+main\\b").matcher(source).results().count();
        assertThat(methodCount).as("exactly one main method, no other method").isEqualTo(1);
    }

    /** AC5: the build plugins and finalName this task's own frozen brief names. */
    @Test
    void buildPluginsAndFinalNameAreRegistered() throws IOException {
        String pom = Files.readString(MODULE_POM);

        assertThat(pom).contains("<artifactId>spring-boot-maven-plugin</artifactId>");
        assertThat(pom).contains("<artifactId>flyway-maven-plugin</artifactId>");
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

    /** Strips XML comments before a negative-proof substring/pattern check. Both this pom and the
     * root pom explain each decision in a comment that may legitimately name the very coordinate
     * or tag the check elsewhere asserts is absent from real content - a plain, uncommented
     * substring search cannot tell the two apart. */
    private static String stripXmlComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?-->", "");
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
