package com.themistra.notification;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
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
        assertThat(pom).as("T14 added the first scheduled job (RetryScheduler), ShedLock-guarded")
                .contains("shedlock-spring")
                .contains("shedlock-provider-jdbc-template");
        assertThat(pom).as("rate limiting is an auth-specific concern (T31/R41), not this task's")
                .doesNotContain("bucket4j");
        // Frontend spec's own Q3: contracts/api/notifications.yaml now exists (the first OpenAPI
        // contract for this service, documenting the already-built in-app stream/read endpoints,
        // R16/R17), parsed by NotificationOpenApiContractTest - mirrors auth-service's and
        // crypto-service's own identical addition for their own first contract file. This T01-era
        // assertion's premise ("no contract exists yet") is no longer true; updated rather than left
        // to assert a fact this repo has since outgrown.
        assertThat(pom).as("notifications.yaml now exists; this dependency parses it")
                .contains("jackson-dataformat-yaml");
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
        assertThat(code).as("T14 added the first scheduled job (RetryScheduler)")
                .contains("@EnableScheduling");
        assertThat(code).as("T14's own scheduled job is ShedLock-guarded")
                .contains("@EnableSchedulerLock");
    }

    /** Kimi Phase 11 Gap 5: T01's own frozen brief "Out" scope excluded any production class beyond
     * the bare Application class - a premature config/entity class added in that task would not
     * fail any other test, since {@link #applicationClassIsBareWithOnlyTheMainMethod()} only reads
     * one named file. T03 ended the bare-skeleton era (7 files); T04 added 4 more; T05 added 3
     * more; T06 added 5 more; T08 added 3 more; T09 added 3 more; T10 added 1 more; T11 added 6
     * more and removed 1; T12 added 8 more and removed 1; T13 added 8 more and removed 1
     * ({@code channel/NoOpInAppChannel.java}, replaced by {@code InAppChannel}); T14 adds 4 more
     * ({@code common/config/ShedLockConfig.java} - the {@code LockProvider} bean
     * {@code RetryScheduler}'s own {@code @SchedulerLock} needs, a small, necessary addition beyond
     * the Phase 5 plan's own named files, mirroring how every prior task's own "exact Maven
     * coordinates confirmed during implementation" note anticipated similarly small wiring details;
     * {@code delivery/DeliveryRetry.java}, {@code delivery/DeliveryRetryRepository.java},
     * {@code delivery/RetryScheduler.java}) and removes none. T07 adds 11 more: the payment
     * consumer and {@code AmountFormatter}, four payment DTOs, and the five-file {@code routing}
     * projection (frozen brief D2). The open-issues sweep (2026-10-19, FU-T07-4) adds the three
     * parked-event files in {@code routing}, and its expiry (2026-10-20) two more:
     * {@code routing/ParkedEventExpiryJob.java} and {@code common/config/ParkedEventProperties.java}.
     * FU-1 layering (2026-10-20) adds {@code inapp/InappNotificationReader.java}. The durable
     * expired-event record (Kimi sweep review, Finding 7, 2026-10-20) adds
     * {@code delivery/UndeliveredEventLog.java} and {@code routing/ExpiredParkedEvent.java}.
     * This list is updated to name all 69
     * explicitly rather than loosened to "at least N files" - an unnamed-count assertion would
     * silently tolerate a stray file no task ever authorized. */
    @Test
    void noExtraProductionClassesExistBeyondT14sOwnAuthorizedSet() throws IOException {
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
                    "channel/InAppChannel.java",
                    "channel/NotificationChannel.java",
                    "channel/SesEmailTransport.java",
                    "common/ApiExceptionHandler.java",
                    "common/ClockConfig.java",
                    "common/PublicEndpoints.java",
                    "common/ResourceServerConfig.java",
                    "common/SecretSafeLogging.java",
                    "common/config/EmailProperties.java",
                    "common/config/EmailTransportStartupValidation.java",
                    "common/config/InappProperties.java",
                    "common/config/LinkProperties.java",
                    "common/config/LinkPropertiesStartupValidation.java",
                    "common/config/ParkedEventProperties.java",
                    "common/config/RetryProperties.java",
                    "common/config/SesClientConfig.java",
                    "common/config/ShedLockConfig.java",
                    "consumer/AmountFormatter.java",
                    "consumer/AuthEventConsumer.java",
                    "consumer/IdempotencyGuard.java",
                    "consumer/NotificationDispatcher.java",
                    "consumer/PaymentEventConsumer.java",
                    "consumer/ProcessedEvent.java",
                    "consumer/ProcessedEventRepository.java",
                    "consumer/dto/EmailRequestedEvent.java",
                    "consumer/dto/InvoiceCreatedEvent.java",
                    "consumer/dto/PaymentFinalizedEvent.java",
                    "consumer/dto/PaymentSeenEvent.java",
                    "consumer/dto/ReceiptIssuedEvent.java",
                    "consumer/dto/UserLifecycleEvent.java",
                    "delivery/DeliveryLog.java",
                    "delivery/DeliveryLogRepository.java",
                    "delivery/DeliveryOrchestrator.java",
                    "delivery/DeliveryRetry.java",
                    "delivery/DeliveryRetryRepository.java",
                    "delivery/RetryScheduler.java",
                    "delivery/UndeliveredEventLog.java",
                    "inapp/InappNotification.java",
                    "inapp/InappNotificationAppender.java",
                    "inapp/InappNotificationReader.java",
                    "inapp/InappNotificationRepository.java",
                    "inapp/InappReadController.java",
                    "inapp/InappStreamController.java",
                    "inapp/InappStreamRegistry.java",
                    "preference/ChannelPreference.java",
                    "preference/ChannelPreferenceRepository.java",
                    "preference/ContactProjection.java",
                    "preference/ContactProjectionRepository.java",
                    "preference/ContactProjectionUpdater.java",
                    "preference/PreferenceResolver.java",
                    "routing/ExpiredParkedEvent.java",
                    "routing/InvoiceRoute.java",
                    "routing/InvoiceRouteRepository.java",
                    "routing/ParkedEvent.java",
                    "routing/ParkedEventExpiryJob.java",
                    "routing/ParkedPaymentEvent.java",
                    "routing/ParkedPaymentEventRepository.java",
                    "routing/PaymentRoute.java",
                    "routing/PaymentRouteRepository.java",
                    "routing/PaymentRouting.java",
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

    // --- T20: the spec status bump and its verification checklist --------------------------

    private static final Path NOTIFICATION_PACKAGE_SPEC = Path.of("../../spec/notification-service/package.md");
    private static final Path NOTIFICATION_DESIGN_SPEC = Path.of("../../spec/notification-service/design.md");
    private static final Path TEST_SOURCES = Path.of("src/test/java");
    /** A backticked {@code Class.method} citation in a §9 line, the class being a test class. */
    private static final Pattern CHECKLIST_CITATION =
            Pattern.compile("`([A-Z][A-Za-z0-9_]*Test)\\.([a-z][A-Za-z0-9_]*)`");
    /** SHA-256 of design.md §4c's four fenced VERBATIM blocks joined by a newline, taken at T20
     * before any annotation was added (frozen brief D2, Phase 5). */
    private static final String SECTION_4C_VERBATIM_SHA256 =
            "9a9ba0fff93ffd2c7528fd75c623099f90d3186960b87358878c3b31f9a70d07";

    /** T20 AC1: the bump stays in place. Matched by table row inside the header table only
     * (before {@code ## 0.}), never by line number (Kimi Phase 3 F2). The Status row is matched by
     * prefix, because its disclosure follows the value. */
    @Test
    void notificationSpecIsBumpedToReadyForImplVersionZeroPointTwo() throws IOException {
        String spec = Files.readString(NOTIFICATION_PACKAGE_SPEC);
        int headerEnd = spec.indexOf("\n## 0. ");
        assertThat(headerEnd).as("package.md must have its header table before section 0").isPositive();
        String header = spec.substring(0, headerEnd);

        assertThat(Pattern.compile("(?m)^\\| Version \\| `0\\.2` \\|\\s*$").matcher(header).find())
                .as("package.md Version must be 0.2").isTrue();
        assertThat(Pattern.compile("(?m)^\\| Status \\| `READY FOR IMPL` — ").matcher(header).find())
                .as("package.md Status must be READY FOR IMPL, followed by its disclosure").isTrue();
        assertThat(Pattern.compile("(?m)^\\| Status \\|[^\\n]*`DRAFT`").matcher(header).find())
                .as("package.md Status must no longer say DRAFT").isFalse();
        assertThat(Pattern.compile("(?m)^\\| Version \\|").matcher(header).results().count())
                .as("the header has exactly one Version row").isEqualTo(1);
        assertThat(Pattern.compile("(?m)^\\| Status \\|").matcher(header).results().count())
                .as("the header has exactly one Status row").isEqualTo(1);
        // The disclosure names the gaps that make items 1-3 partial (D1, Kimi Phase 3 F3), and,
        // since the open-issues sweep (2026-10-19), the follow-ups it closed.
        assertThat(header).contains("FU-T07-1", "FU-T07-2", "FU-T29-1", "FU-T07-4", "FU-T20-1", "Closed on 2026-10-19");
    }

    /** T20 AC3/AC6: every test §9 cites as evidence exists as a JUnit test in that class's own
     * file and is enabled, so a rename can't leave the spec pointing at nothing. The one exception: an ArchUnit
     * rule field is accepted where the class runs it from a plain-test canary (the Surefire
     * {@code @ArchTest} workaround). */
    @Test
    void everyTestNamedInTheVerificationChecklistExists() throws IOException {
        Map<String, String> testSourcesByClass = testSourcesByClass();
        List<String> missing = new ArrayList<>();
        int citations = 0;
        for (String item : verificationChecklistItems()) {
            Matcher matcher = CHECKLIST_CITATION.matcher(item);
            while (matcher.find()) {
                citations++;
                String source = testSourcesByClass.get(matcher.group(1));
                if (!declaresTestMethod(source, matcher.group(2))) {
                    missing.add(matcher.group(1) + "." + matcher.group(2));
                }
            }
        }
        assertThat(citations).as("the parse must find the checklist's citations, not pass vacuously")
                .isGreaterThanOrEqualTo(25);
        assertThat(missing).as("§9 cites tests that do not exist").isEmpty();
    }

    /** T20 AC3 (Kimi Phase 3 F4): items 1-3 stay visibly partial, each keeping the reason that
     * makes it partial; the other eight stay ticked. Nothing can be ticked silently. Item 6 was
     * partial from T20 Phase 9 (the in-app verify body carried its link) until the open-issues
     * sweep closed FU-T20-1 on 2026-10-19; it is now ticked and must say so. */
    @Test
    void verificationChecklistKeepsItsPartialItemsUnticked() throws IOException {
        List<String> items = verificationChecklistItems();
        assertThat(items).as("§9 has 11 items").hasSize(11);

        for (int itemNumber = 1; itemNumber <= 3; itemNumber++) {
            assertThat(items.get(itemNumber - 1)).as("§9 item %d stays visibly partial", itemNumber)
                    .startsWith("- [ ] **Partial —** ");
        }
        assertThat(items.get(0)).as("item 1 says what the two drifted named tests prove")
                .contains("merchant-only", "FU-T07-1", "FU-T07-2 closed");
        assertThat(items.get(1)).as("item 2 names the L2 interpretation").contains("L2", "interpreted");
        assertThat(items.get(2)).as("item 3 names the recipients deviation").contains("merchant only");
        // Open-issues sweep (2026-10-19): FU-T20-1 closed, so item 6 is ticked, with its reason.
        assertThat(items.get(5)).as("§9 item 6 is ticked, naming the closed follow-up").startsWith("- [x] ")
                .contains("FU-T20-1 closed");
        for (int itemNumber : new int[] {4, 5, 6, 7, 8, 9, 10, 11}) {
            assertThat(items.get(itemNumber - 1)).as("§9 item %d stays ticked", itemNumber).startsWith("- [x] ");
        }
        assertThat(items.stream().filter(item -> item.startsWith("- [x] ")).count())
                .as("exactly 8 §9 items are ticked").isEqualTo(8);
        assertThat(items).as("every §9 marker is a GFM checkbox")
                .allMatch(item -> item.startsWith("- [ ] ") || item.startsWith("- [x] "));
    }

    /** T20 AC4 (frozen brief D2, Kimi Phase 3 F8): the dated recipient note sits beside §4c, but
     * §4c's fenced VERBATIM artifacts stay byte-identical to what was authored. */
    @Test
    void verbatimSection4cIsUnchanged() throws IOException, NoSuchAlgorithmException {
        String design = Files.readString(NOTIFICATION_DESIGN_SPEC);
        int start = design.indexOf("## 4c.");
        int end = design.indexOf("\n## ", start + 1);
        assertThat(start).as("design.md must have §4c").isNotNegative();
        assertThat(end).as("§4c must be followed by another section").isGreaterThan(start);
        List<String> fences = Pattern.compile("```[^\\n]*\\n.*?\\n```", Pattern.DOTALL)
                .matcher(design.substring(start, end)).results().map(java.util.regex.MatchResult::group).toList();
        assertThat(fences).as("§4c has its four verbatim blocks").hasSize(4);

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", fences).getBytes(StandardCharsets.UTF_8));
        assertThat(HexFormat.of().formatHex(digest)).isEqualTo(SECTION_4C_VERBATIM_SHA256);
    }

    /** T20 Phase 10 (AC2/AC4): §9 items 2 and 3 count as "no silent deviation" only because the
     * deviations are written down beside the text they deviate from. This keeps those dated notes
     * in place: the L2 interpretation sits directly under L2, the recipients note sits in §4c
     * after its mapping block, and R3/R5 keep their partial notes. */
    @Test
    void specKeepsItsDatedDeviationNotesBesideTheTextTheyQualify() throws IOException {
        String design = Files.readString(NOTIFICATION_DESIGN_SPEC);
        List<String> designLines = design.lines().toList();
        int l2 = designLines.indexOf(designLines.stream().filter(line -> line.startsWith("- L2. ")).findFirst()
                .orElseThrow());
        assertThat(designLines.get(l2 + 1)).as("the L2 interpretation sits directly under L2")
                .startsWith("  - *Interpretation (2026-10-19").contains("invoice_routes");

        int section4c = design.indexOf("## 4c.");
        int section4cEnd = design.indexOf("\n## ", section4c + 1);
        String section = design.substring(section4c, section4cEnd);
        int mappingEnd = section.indexOf("`chain.*` events are NOT consumed");
        int note = section.indexOf("> **Recipients as shipped (2026-10-19");
        assertThat(mappingEnd).as("§4c keeps its chain.* line").isPositive();
        assertThat(note).as("the recipients note follows the mapping inside §4c").isGreaterThan(mappingEnd);
        assertThat(section.substring(note)).contains("**merchant only**", "**receipt link**", "FU-T07-2 closed",
                "**not mapped**");

        String requirements = Files.readString(Path.of("../../spec/notification-service/requirements.md"));
        for (String requirement : List.of("R3", "R5")) {
            int start = requirements.indexOf("- " + requirement + ". ");
            int next = requirements.indexOf("\n- R", start + 1);
            assertThat(requirements.substring(start, next)).as("%s keeps its partial note", requirement)
                    .contains("*Note (2026-10-19, T07/T20): **partial.**");
        }
    }

    /** {@code @Disabled}, simple or fully qualified. */
    private static final String DISABLED = "@(?:[A-Za-z0-9_]+\\.)*Disabled\\b";

    /** True when {@code source} declares {@code member} as an enabled JUnit test method, or as an
     * ArchUnit rule field the same class runs through {@code member.check(}. The method's
     * annotations may come in any order (Kimi Phase 8 F2). Neither the method nor its class may
     * be {@code @Disabled}, so "exists" also means "runs" (F5). */
    private static boolean declaresTestMethod(String source, String member) {
        if (source == null || Pattern.compile(DISABLED + "[^{]*\\bclass\\s").matcher(source).find()) {
            return false;
        }
        Matcher method = Pattern.compile("((?:@[A-Za-z0-9_.]+(?:\\([^)]*\\))?\\s*)+)"
                + "(?:public\\s+|protected\\s+)?void\\s+" + Pattern.quote(member) + "\\s*\\(").matcher(source);
        while (method.find()) {
            String annotations = method.group(1);
            if (Pattern.compile("@(?:[A-Za-z0-9_]+\\.)*(?:Test|ParameterizedTest)\\b").matcher(annotations).find()
                    && !Pattern.compile(DISABLED).matcher(annotations).find()) {
                return true;
            }
        }
        return Pattern.compile("ArchRule\\s+" + Pattern.quote(member) + "\\s*=").matcher(source).find()
                && source.contains(member + ".check(");
    }

    /** True when {@code member} is declared as an enabled test (or checked rule) in any test file. */
    private static boolean declaredInAnyTestFile(Map<String, String> testSourcesByClass, String member) {
        return testSourcesByClass.values().stream().anyMatch(source -> declaresTestMethod(source, member));
    }

    /** All test sources under {@code src/test/java}, keyed by class name. */
    private static Map<String, String> testSourcesByClass() throws IOException {
        Map<String, String> testSourcesByClass = new HashMap<>();
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                testSourcesByClass.put(name.substring(0, name.length() - ".java".length()), Files.readString(file));
            }
        }
        return testSourcesByClass;
    }

    /** T20 Phase 9 (self-review S1, Kimi Phase 8 F1/F3): every test §8 names exists and is enabled
     * somewhere under {@code src/test/java}, so §9 item 1's "all 20 names exist" is enforced.
     * L11's name is an ArchUnit rule field run by a canary, which exercises the rule-field branch. */
    @Test
    void everyTestNamedInTheTestPlanExists() throws IOException {
        String spec = Files.readString(NOTIFICATION_PACKAGE_SPEC);
        int start = spec.indexOf("## 8. Test plan");
        int end = spec.indexOf("## 9. ", start);
        assertThat(start).as("package.md must have its §8 test plan").isNotNegative();
        assertThat(end).as("§8 must be followed by §9").isGreaterThan(start);
        List<java.util.regex.MatchResult> entries = Pattern.compile("(?m)^- `(should[A-Za-z0-9_]+)` → ([RL][0-9]+)\\s*$")
                .matcher(spec.substring(start, end)).results().toList();
        List<String> names = entries.stream().map(match -> match.group(1)).toList();
        assertThat(names).as("§8 names exactly 20 distinct tests").hasSize(20).doesNotHaveDuplicates()
                .contains("shouldPreventCrossModuleEntityImports");
        // Phase 11 Gap 4: one named test per requirement R1-R19, plus L11, none listed twice.
        List<String> expectedTargets = new ArrayList<>();
        for (int requirement = 1; requirement <= 19; requirement++) {
            expectedTargets.add("R" + requirement);
        }
        expectedTargets.add("L11");
        assertThat(entries.stream().map(match -> match.group(2)).toList())
                .as("§8 maps exactly once to each of R1-R19 and L11").containsExactlyInAnyOrderElementsOf(expectedTargets);

        Map<String, String> sources = testSourcesByClass();
        assertThat(names).as("§8 names tests that do not exist or are disabled")
                .allMatch(name -> declaredInAnyTestFile(sources, name));
    }

    /** The {@code - [} item lines of {@code package.md} §9, in order. */
    private static List<String> verificationChecklistItems() throws IOException {
        String spec = Files.readString(NOTIFICATION_PACKAGE_SPEC);
        int start = spec.indexOf("## 9. Verification checklist");
        int end = spec.indexOf("## 10. ", start);
        assertThat(start).as("package.md must have its §9 checklist").isNotNegative();
        assertThat(end).as("§9 must be followed by §10").isGreaterThan(start);
        return spec.substring(start, end).lines().filter(line -> line.startsWith("- [")).toList();
    }
}
