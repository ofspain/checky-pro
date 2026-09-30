package com.themistra.notification;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    /** AC1: services/notification is registered in the root reactor, after both existing services.
     * Kimi Phase 11 Gap 1: chains all three positions rather than only checking the last pair - the
     * original version would have passed a reorder to auth/notification/crypto just as easily as the
     * intended auth/crypto/notification. */
    @Test
    void rootPomRegistersNotificationServiceAfterAuthAndCrypto() throws IOException {
        String rootPom = Files.readString(ROOT_POM);

        assertThat(rootPom).contains("<module>services/auth</module>");
        assertThat(rootPom).contains("<module>services/crypto</module>");
        assertThat(rootPom).contains("<module>services/notification</module>");

        int authIdx = rootPom.indexOf("<module>services/auth</module>");
        int cryptoIdx = rootPom.indexOf("<module>services/crypto</module>");
        int notificationIdx = rootPom.indexOf("<module>services/notification</module>");
        assertThat(authIdx).as("services/auth must be listed before services/crypto").isLessThan(cryptoIdx);
        assertThat(cryptoIdx).as("services/crypto must be listed before services/notification (dependency order)")
                .isLessThan(notificationIdx);
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
        assertThat(hasGroupAndArtifact(pom, "software.amazon.awssdk", "sesv2"))
                .as("SES v2 client, correct groupId (Kimi Phase 8 Finding #3)").isTrue();
        assertThat(pom).contains("spring-boot-starter-actuator");
        assertThat(pom).contains("micrometer-registry-prometheus");
        assertThat(pom).contains("spring-boot-starter-test");
        assertThat(pom).contains("spring-security-test");
        assertThat(pom).contains("spring-boot-testcontainers");
        assertThat(hasGroupAndArtifact(pom, "org.testcontainers", "postgresql"))
                .as("Testcontainers Postgres module").isTrue();
        assertThat(hasGroupAndArtifact(pom, "org.testcontainers", "kafka"))
                .as("Testcontainers Kafka module (Kimi Phase 8 Finding #2)").isTrue();
        assertThat(hasGroupAndArtifact(pom, "org.testcontainers", "junit-jupiter"))
                .as("Testcontainers JUnit 5 integration (Kimi Phase 8 Finding #2)").isTrue();
        assertThat(pom).contains("archunit-junit5");
        assertThat(pom).contains("awaitility");
        assertThat(pom).as("notification-service validates tokens, it never issues them")
                .doesNotContain("oauth2-authorization-server");
        // Kimi Phase 8 Finding #4: guards against blindly copying auth-specific dependencies this
        // task's own "shared subset only" scope excludes.
        assertThat(pom).as("ShedLock is not needed until a scheduled job exists (task 14)")
                .doesNotContain("shedlock-spring")
                .doesNotContain("shedlock-provider-jdbc-template");
        assertThat(pom).as("rate limiting is an auth-specific concern (T31/R41), not this task's")
                .doesNotContain("bucket4j");
        assertThat(pom).as("no OpenAPI YAML contract exists for this service yet")
                .doesNotContain("jackson-dataformat-yaml");
    }

    private record Coordinate(String groupId, String artifactId) {
    }

    /** Kimi Phase 11 Gap 3: every test-only dependency in this pom - a scope regression to
     * {@code compile} for any of these would leak test infrastructure into the production artifact
     * without failing any other test. */
    private static final List<Coordinate> TEST_SCOPED_DEPENDENCIES = List.of(
            new Coordinate("org.springframework.boot", "spring-boot-starter-test"),
            new Coordinate("org.springframework.security", "spring-security-test"),
            new Coordinate("org.springframework.boot", "spring-boot-testcontainers"),
            new Coordinate("org.testcontainers", "postgresql"),
            new Coordinate("org.testcontainers", "kafka"),
            new Coordinate("org.testcontainers", "junit-jupiter"),
            new Coordinate("com.tngtech.archunit", "archunit-junit5"),
            new Coordinate("org.awaitility", "awaitility"));

    /** Kimi Phase 8 Finding #6, extended at Phase 11 (Kimi Gaps #2/#3): dependency presence alone
     * doesn't guard scope - a future edit could flip the runtime JDBC driver to compile scope, or any
     * test-only dependency to compile scope, without failing any other assertion. */
    @Test
    void runtimeAndTestScopesAreCorrect() throws IOException {
        String pom = Files.readString(MODULE_POM);

        assertThat(dependencyScope(pom, "org.postgresql", "postgresql")).isEqualTo("runtime");
        assertThat(dependencyScope(pom, "io.micrometer", "micrometer-registry-prometheus"))
                .isEqualTo("runtime");
        for (Coordinate c : TEST_SCOPED_DEPENDENCIES) {
            assertThat(dependencyScope(pom, c.groupId(), c.artifactId()))
                    .as("%s:%s must be test-scoped", c.groupId(), c.artifactId())
                    .isEqualTo("test");
        }
    }

    private static String dependencyScope(String pomContent, String groupId, String artifactId) {
        // Some dependencies (archunit-junit5, awaitility) carry an explicit <version> between
        // <artifactId> and <scope>, since they're not managed by the parent Spring Boot BOM - the
        // optional (?:...)? group tolerates either shape.
        String pattern = "<groupId>" + Pattern.quote(groupId) + "</groupId>\\s*<artifactId>"
                + Pattern.quote(artifactId) + "</artifactId>\\s*(?:<version>[^<]+</version>\\s*)?"
                + "<scope>([^<]+)</scope>";
        return extractFirst(pomContent, pattern, groupId + ":" + artifactId + " scope");
    }

    private static boolean hasGroupAndArtifact(String pomContent, String groupId, String artifactId) {
        String pattern = "<groupId>" + Pattern.quote(groupId) + "</groupId>\\s*<artifactId>"
                + Pattern.quote(artifactId) + "</artifactId>";
        return Pattern.compile(pattern).matcher(pomContent).find();
    }

    /** Kimi Phase 3 Finding #5/#8, tightened at Phase 8 (Kimi Findings #1/#5): build output naming and
     * the local-dev Flyway plugin mirror the sibling-service convention exactly, except the schema
     * name - and, critically, the plugin is never bound to the Maven lifecycle (the brief's own text:
     * "runs solely via explicit `mvn flyway:migrate`, never during `package`/`verify`/CI"). The
     * version check is now scoped to the plugin's own extracted block, not a global substring search -
     * a coincidentally-matching version elsewhere in the pom would no longer satisfy it. */
    @Test
    void finalNameAndFlywayPluginMirrorTheSiblingConvention() throws IOException {
        String pom = Files.readString(MODULE_POM);
        String flywayPlugin = pluginBlock(pom, "flyway-maven-plugin");

        assertThat(pom).contains("<finalName>notification-service</finalName>");
        assertThat(dependencyVersion(flywayPlugin, "flyway-maven-plugin")).isEqualTo("11.7.2");
        assertThat(flywayPlugin).contains("<schemas>notifications</schemas>");
        assertThat(flywayPlugin)
                .as("must never bind to package/verify/CI - local-dev-only via explicit mvn flyway:migrate")
                .doesNotContain("<executions>");
    }

    private static String pluginBlock(String pomContent, String artifactId) {
        String pattern = "(?s)<plugin>\\s*<groupId>[^<]*</groupId>\\s*<artifactId>"
                + Pattern.quote(artifactId) + "</artifactId>.*?</plugin>";
        Matcher matcher = Pattern.compile(pattern).matcher(pomContent);
        if (!matcher.find()) {
            throw new AssertionError("no <plugin> block found for artifactId " + artifactId);
        }
        return matcher.group();
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
        assertThat(code).as("T03 added the first @ConfigurationProperties classes to scan")
                .contains("@ConfigurationPropertiesScan");
        assertThat(code).as("no scheduled job exists yet")
                .doesNotContain("@EnableScheduling");
        assertThat(code).as("no ShedLock-guarded job exists yet")
                .doesNotContain("@EnableSchedulerLock");
    }

    /** Kimi Phase 11 Gap 5: T01's own frozen brief "Out" scope excluded any production class beyond
     * the bare Application class - a premature config/entity class added in that task would not
     * fail any other test, since {@link #applicationClassIsBareWithOnlyTheMainMethod()} only reads
     * one named file. T03 ended the bare-skeleton era (7 files); T04 added 4 more; T05 added 3
     * more; T06 added 5 more; T08 added 3 more; T09 added 3 more; T10 added 1 more; T11 added 6
     * more and removed 1; T12 adds 7 more
     * ({@code channel/{EmailChannel,EmailDeliveryException,EmailMessage,EmailTransport,
     * FakeEmailTransport,SesEmailTransport}}, {@code common/config/SesClientConfig.java}) and
     * removes 1 ({@code channel/NoOpEmailChannel.java}, replaced by {@code EmailChannel} as the
     * real {@code EMAIL} {@code NotificationChannel} implementation - pre-authorized since T11's
     * own Javadoc). This list is updated to name all 38 explicitly rather than loosened to "at
     * least N files" - an unnamed-count assertion would silently tolerate a stray file no task ever
     * authorized. */
    @Test
    void noExtraProductionClassesExistBeyondT12sOwnAuthorizedSet() throws IOException {
        Path mainSourceDir = Path.of("src/main/java/com/themistra/notification");

        try (Stream<Path> files = Files.walk(mainSourceDir)) {
            List<String> javaFiles = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(p -> mainSourceDir.relativize(p).toString())
                    .sorted()
                    .toList();
            assertThat(javaFiles).containsExactly(
                    "NotificationServiceApplication.java",
                    "channel/EmailChannel.java",
                    "channel/EmailDeliveryException.java",
                    "channel/EmailMessage.java",
                    "channel/EmailTransport.java",
                    "channel/FakeEmailTransport.java",
                    "channel/NoOpInAppChannel.java",
                    "channel/NotificationChannel.java",
                    "channel/SesEmailTransport.java",
                    "common/ClockConfig.java",
                    "common/PublicEndpoints.java",
                    "common/ResourceServerConfig.java",
                    "common/SecretSafeLogging.java",
                    "common/config/EmailProperties.java",
                    "common/config/InappProperties.java",
                    "common/config/LinkProperties.java",
                    "common/config/LinkPropertiesStartupValidation.java",
                    "common/config/RetryProperties.java",
                    "common/config/SesClientConfig.java",
                    "consumer/AuthEventConsumer.java",
                    "consumer/IdempotencyGuard.java",
                    "consumer/NotificationDispatcher.java",
                    "consumer/ProcessedEvent.java",
                    "consumer/ProcessedEventRepository.java",
                    "consumer/dto/EmailRequestedEvent.java",
                    "consumer/dto/UserLifecycleEvent.java",
                    "delivery/DeliveryLog.java",
                    "delivery/DeliveryLogRepository.java",
                    "delivery/DeliveryOrchestrator.java",
                    "preference/ChannelPreference.java",
                    "preference/ChannelPreferenceRepository.java",
                    "preference/ContactProjection.java",
                    "preference/ContactProjectionRepository.java",
                    "preference/ContactProjectionUpdater.java",
                    "preference/PreferenceResolver.java",
                    "template/Template.java",
                    "template/TemplateRenderer.java",
                    "template/TemplateRepository.java");
        }
    }

    /** Kimi Phase 11 Gap 6: the brief requires SES to be added via a {@code dependencyManagement}
     * import specifically (so its version stays centrally managed and aligned across dependents),
     * not merely declared as a regular dependency that happens to carry the right version string -
     * {@link #sharedDependencyVersionsStayAlignedWithAuthAndCrypto()} only proves the latter. */
    @Test
    void awsSdkBomIsImportedNotMerelyDeclaredAtTheRightVersion() throws IOException {
        String pom = Files.readString(MODULE_POM);
        String dependencyManagement = dependencyManagementBlock(pom);

        assertThat(dependencyManagement).contains("<groupId>software.amazon.awssdk</groupId>");
        assertThat(dependencyManagement).contains("<artifactId>bom</artifactId>");
        assertThat(dependencyManagement).contains("<version>2.50.2</version>");
        assertThat(dependencyManagement).contains("<type>pom</type>");
        assertThat(dependencyManagement).contains("<scope>import</scope>");
    }

    private static String dependencyManagementBlock(String pomContent) {
        String pattern = "(?s)<dependencyManagement>.*?</dependencyManagement>";
        Matcher matcher = Pattern.compile(pattern).matcher(pomContent);
        if (!matcher.find()) {
            throw new AssertionError("no <dependencyManagement> block found");
        }
        return matcher.group();
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
        // Kimi Phase 8 Finding #7: the brief says the Flyway plugin block mirrors auth/crypto
        // exactly except the schema - the version-alignment check should say so too.
        assertThat(dependencyVersion(notificationPom, "flyway-maven-plugin"))
                .isEqualTo(dependencyVersion(authPom, "flyway-maven-plugin"))
                .isEqualTo(dependencyVersion(cryptoPom, "flyway-maven-plugin"));
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
