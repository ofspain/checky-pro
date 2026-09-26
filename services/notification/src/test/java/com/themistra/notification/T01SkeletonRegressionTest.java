package com.themistra.notification;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T01 — permanent regression guards for this task's own acceptance criteria (AC1-AC5), which are
 * otherwise only checked by file reads and {@code mvn validate} during the review phases and would
 * not fail a later build if silently reverted. Plain JUnit, deliberately not ArchUnit, matching
 * {@code services/crypto}'s own {@code T01SkeletonRegressionTest} - a "this content must be
 * present/absent" scan, not a structural bytecode analysis - there is no production code yet for a
 * structural rule to analyze.
 */
class T01SkeletonRegressionTest {

    private static final Path MODULE_POM = Path.of("pom.xml");
    private static final Path ROOT_POM = Path.of("../../pom.xml");
    private static final Path AUTH_POM = Path.of("../auth/pom.xml");
    private static final Path CRYPTO_POM = Path.of("../crypto/pom.xml");
    private static final Path APPLICATION_JAVA =
            Path.of("src/main/java/com/themistra/notification/NotificationServiceApplication.java");

    /** AC1: services/notification is registered in the root reactor, after both existing services. */
    @Test
    void rootPomRegistersNotificationServiceAfterAuthAndCrypto() throws IOException {
        String rootPom = Files.readString(ROOT_POM);

        assertThat(rootPom).contains("<module>services/auth</module>");
        assertThat(rootPom).contains("<module>services/crypto</module>");
        assertThat(rootPom).contains("<module>services/notification</module>");
        assertThat(rootPom.indexOf("<module>services/crypto</module>"))
                .as("services/crypto must be listed before services/notification (dependency order)")
                .isLessThan(rootPom.indexOf("<module>services/notification</module>"));
    }

    /** AC2 (Kimi Phase 3 Findings #1/#2): every required dependency is present by its exact artifact,
     * and the issuer starter (this service never issues tokens) is absent. {@code postgresql} appears
     * twice in this pom (the runtime JDBC driver, {@code org.postgresql:postgresql}, and the
     * Testcontainers module, {@code org.testcontainers:postgresql}) - a plain {@code contains}
     * wouldn't notice either one going missing while the other remains, so both are checked by their
     * groupId+artifactId pair specifically (self-review catch: the original version only checked the
     * bare artifactId string once). */
    @Test
    void notificationPomDeclaresTheRequiredDependenciesAndExcludesTheIssuerStarter() throws IOException {
        String pom = Files.readString(MODULE_POM);

        assertThat(pom).contains("spring-boot-starter-web");
        assertThat(pom).contains("spring-boot-starter-validation");
        assertThat(pom).contains("spring-boot-starter-oauth2-resource-server");
        assertThat(pom).contains("spring-security-oauth2-resource-server");
        assertThat(pom).contains("spring-boot-starter-data-jpa");
        assertThat(pom).contains("flyway-core");
        assertThat(pom).contains("flyway-database-postgresql");
        assertThat(hasGroupAndArtifact(pom, "org.postgresql", "postgresql"))
                .as("runtime JDBC driver").isTrue();
        assertThat(pom).contains("spring-kafka");
        assertThat(pom).contains("<artifactId>sesv2</artifactId>");
        assertThat(pom).contains("spring-boot-starter-actuator");
        assertThat(pom).contains("micrometer-registry-prometheus");
        assertThat(pom).contains("spring-boot-starter-test");
        assertThat(pom).contains("spring-security-test");
        assertThat(pom).contains("spring-boot-testcontainers");
        assertThat(hasGroupAndArtifact(pom, "org.testcontainers", "postgresql"))
                .as("Testcontainers Postgres module").isTrue();
        assertThat(pom).contains("<artifactId>kafka</artifactId>");
        assertThat(pom).contains("<artifactId>junit-jupiter</artifactId>");
        assertThat(pom).contains("archunit-junit5");
        assertThat(pom).contains("awaitility");
        assertThat(pom).as("notification-service validates tokens, it never issues them")
                .doesNotContain("oauth2-authorization-server");
    }

    private static boolean hasGroupAndArtifact(String pomContent, String groupId, String artifactId) {
        String pattern = "<groupId>" + Pattern.quote(groupId) + "</groupId>\\s*<artifactId>"
                + Pattern.quote(artifactId) + "</artifactId>";
        return Pattern.compile(pattern).matcher(pomContent).find();
    }

    /** Kimi Phase 3 Finding #5/#8: build output naming and the local-dev Flyway plugin mirror the
     * sibling-service convention exactly, except the schema name. */
    @Test
    void finalNameAndFlywayPluginMirrorTheSiblingConvention() throws IOException {
        String pom = Files.readString(MODULE_POM);

        assertThat(pom).contains("<finalName>notification-service</finalName>");
        assertThat(pom).contains("<artifactId>flyway-maven-plugin</artifactId>");
        assertThat(pom).contains("<version>11.7.2</version>");
        assertThat(pom).contains("<schemas>notifications</schemas>");
    }

    /** Kimi Phase 3 Finding #3/#6: the skeleton Application class is bare - annotated, has a real
     * {@code main} method (the exact gap that would silently break {@code spring-boot:repackage}),
     * and carries none of the annotations later tasks introduce for the things they add. Scoped to
     * the code after the class-level Javadoc, not the whole file - that Javadoc itself names these
     * same three annotations in prose, explaining why they're absent, which would otherwise trip a
     * naive whole-file {@code doesNotContain} check against its own explanatory comment. */
    @Test
    void applicationClassIsBareWithOnlyTheMainMethod() throws IOException {
        String app = Files.readString(APPLICATION_JAVA);
        String code = app.replaceFirst("(?s)/\\*\\*.*?\\*/", "");

        assertThat(app).contains("package com.themistra.notification;");
        assertThat(code).contains("@SpringBootApplication");
        assertThat(code).contains("SpringApplication.run(NotificationServiceApplication.class, args);");
        assertThat(code).as("no @ConfigurationProperties class exists yet to scan")
                .doesNotContain("@ConfigurationPropertiesScan");
        assertThat(code).as("no scheduled job exists yet")
                .doesNotContain("@EnableScheduling");
        assertThat(code).as("no ShedLock-guarded job exists yet")
                .doesNotContain("@EnableSchedulerLock");
    }

    /** Kimi Phase 3 Finding #4 (T01 Phase 4 resolution): verified directly via {@code
     * dependency:tree} that {@code sesv2} resolves a working HTTP client transitively (both
     * {@code apache5-client} and {@code netty-nio-client}), same as the sibling services' own KMS/S3
     * usage under the identical BOM version - no explicit HTTP-client artifact was added. This test is
     * the empirical proof, not just the dependency-tree observation: constructing a real
     * {@code SesV2Client} is exactly where a missing HTTP-client implementation would throw
     * {@code SdkClientException}, with no real AWS call made. */
    @Test
    void sesV2ClientCanActuallyBeConstructed() {
        try (SesV2Client client = SesV2Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build()) {
            assertThat(client).isNotNull();
        }
    }

    /** Kimi Phase 3 Finding #7: dependencies intentionally pinned to match auth/crypto (same Spring
     * Boot version, same Testcontainers-Docker-handshake fix, same AWS SDK BOM version) do not
     * silently drift apart. Extends crypto's own two-service check to all three siblings. */
    @Test
    void sharedDependencyVersionsStayAlignedWithAuthAndCrypto() throws IOException {
        String notificationPom = Files.readString(MODULE_POM);
        String authPom = Files.readString(AUTH_POM);
        String cryptoPom = Files.readString(CRYPTO_POM);

        assertThat(propertyValue(notificationPom, "testcontainers.version"))
                .isEqualTo(propertyValue(authPom, "testcontainers.version"))
                .isEqualTo(propertyValue(cryptoPom, "testcontainers.version"));
        assertThat(dependencyVersion(notificationPom, "archunit-junit5"))
                .isEqualTo(dependencyVersion(authPom, "archunit-junit5"))
                .isEqualTo(dependencyVersion(cryptoPom, "archunit-junit5"));
        assertThat(dependencyVersion(notificationPom, "awaitility"))
                .isEqualTo(dependencyVersion(authPom, "awaitility"))
                .isEqualTo(dependencyVersion(cryptoPom, "awaitility"));
        assertThat(dependencyVersion(notificationPom, "bom"))
                .as("AWS SDK BOM version (dependencyManagement)")
                .isEqualTo(dependencyVersion(authPom, "bom"))
                .isEqualTo(dependencyVersion(cryptoPom, "bom"));
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
