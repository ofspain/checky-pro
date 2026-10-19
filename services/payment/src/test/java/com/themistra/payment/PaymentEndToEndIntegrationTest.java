package com.themistra.payment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.themistra.payment.common.CanonicalJson;
import com.themistra.payment.receipt.ledger.LedgerVerificationResult;
import com.themistra.payment.receipt.ledger.LedgerVerifier;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectLockRetention;
import software.amazon.awssdk.services.s3.model.ObjectLockRetentionMode;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T26: the end-to-end proof that the whole payment flow composes through the real wiring.
 * Each step was built and tested on its own in T05–T19. This test runs them together:
 * HTTP security, the Kafka listeners, the transaction boundaries, {@code OutboxRelay}, S3,
 * the ledger, and the Crypto Service HTTP calls. Production code is untouched.
 *
 * <p><b>Infrastructure (frozen brief D1–D3).</b>
 * <ul>
 *   <li>Testcontainers Postgres and Kafka. The Kafka is a container ({@code apache/kafka:3.8.0}),
 *       not the shared local broker T14 uses, matching task 26's own text and the auth and crypto
 *       end-to-end tests.</li>
 *   <li>LocalStack 3.8 S3, with an Object Lock bucket. Phase 2 probed it: COMPLIANCE retention is
 *       really kept, and deleting a locked version is really refused. So the WORM assertions below
 *       (L9) are genuine.</li>
 *   <li>A WireMock stand-in for Crypto Service and auth-service's token endpoint (task 26,
 *       {@code package.md:74}).</li>
 * </ul>
 *
 * <p><b>HTTP entry point (D4, Phase 3 F6).</b> MockMvc, not a real port. The invoice request
 * still goes through the real security filter chain, {@code JwtRoleAuthoritiesConverter} and
 * {@code @PreAuthorize}, which is what L10 cares about. Only the JWKS signature check is stubbed,
 * through {@code @MockBean JwtDecoder}; {@code ResourceServerConfigTest} covers that check on its
 * own. Spring Boot owns the servlet transport, and it is tested there.
 *
 * <p><b>Context shape (D6).</b> This copies T14's {@code ChainEventConsumerIdempotencyAndOrderingIntegrationTest}:
 * <ul>
 *   <li>{@code @EntityScan} is limited to the six feature packages. This is required:
 *       {@code archtestfixtures.RogueUnmappedEntity} would otherwise break Hibernate
 *       startup.</li>
 *   <li>Migration runs through Flyway, and the app connects as {@code payment_app}.</li>
 *   <li>Verification reads Postgres through raw admin JDBC. Under L12, no entity class is
 *       reachable from this cross-module test.</li>
 * </ul>
 *
 * <p><b>Out of scope (Phase 4).</b>
 * <ul>
 *   <li>A direct {@code SEEN → FINALIZED} end-to-end run (F5).
 *       {@code PaymentServiceTest.shouldTransitionToFinalizedFromSeenWhenNeverConfirmed} covers
 *       it.</li>
 *   <li>Crypto-service's real watcher-unregister behaviour. Crypto Service is a stand-in here, so a
 *       green run of this class proves nothing about that cross-service flow.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.kafka.consumer.group-id=payment-e2e-it",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.consumer.properties.metadata.max.age.ms=1000",
                "themistra.payment.outbox.relay-interval-ms=200"
        })
@AutoConfigureMockMvc
@EntityScan(basePackages = {
        "com.themistra.payment.invoice",
        "com.themistra.payment.payment",
        "com.themistra.payment.events",
        "com.themistra.payment.verification",
        "com.themistra.payment.consumer",
        "com.themistra.payment.receipt"
})
class PaymentEndToEndIntegrationTest {

    private static final String PAYMENT_APP_PASSWORD = "e2e-payment-app-password";
    private static final String BUCKET = "e2e-receipts";
    private static final String RECEIPT_PREFIX = "receipts/";
    private static final String CHAIN = "ETHEREUM";
    private static final String TOKEN_CONTRACT_ADDRESS = "0x111";
    private static final String PAY_TO_ADDRESS = "0xdef";
    private static final String GENESIS_PREV_HASH = "0".repeat(64);
    private static final String WATCHES_PATH = "/internal/v1/watches";
    private static final String ATTEST_PATH = "/internal/v1/attest";
    private static final String TOKEN_PATH = "/oauth2/token";
    private static final String CRYPTO_BEARER = "Bearer e2e-crypto-token";
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(60);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> TOPICS = List.of(
            "chain.tx.seen", "chain.tx.confirmed", "chain.tx.finalized", "chain.tx.reorged",
            "payments.invoice.created", "payments.payment.seen", "payments.payment.finalized",
            "payments.receipt.issued");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @Container
    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.S3);

    /** Started in a static initialiser because {@code @DynamicPropertySource} needs its port
     * before the Spring context is built. */
    private static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    private static S3Client s3;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "payment_app");
        registry.add("spring.datasource.password", () -> PAYMENT_APP_PASSWORD);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("themistra.payment.crypto.base-url", WIREMOCK::baseUrl);
        registry.add("themistra.payment.crypto.auth.token-uri", () -> WIREMOCK.baseUrl() + TOKEN_PATH);
        registry.add("themistra.payment.receipt.bucket", () -> BUCKET);
        registry.add("themistra.payment.receipt.prefix", () -> RECEIPT_PREFIX);
        registry.add("themistra.payment.receipt.region", LOCALSTACK::getRegion);
    }

    @BeforeAll
    static void migrateAndProvisionLocalPassword() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("payments")
                .load()
                .migrate();
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("ALTER ROLE payment_app PASSWORD '" + PAYMENT_APP_PASSWORD + "'");
        }
    }

    /** The topics are created before any listener or assertion consumer subscribes. On a fresh
     * broker, a consumer subscribed to a topic that doesn't exist yet only notices it on a later
     * metadata refresh. That would show up as a confusing Awaitility timeout. */
    @BeforeAll
    static void createTopics() throws InterruptedException, ExecutionException, TimeoutException {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(TOPICS.stream().map(topic -> new NewTopic(topic, 1, (short) 1)).toList())
                    .all().get(30, TimeUnit.SECONDS);
        }
    }

    @BeforeAll
    static void createObjectLockBucket() {
        s3 = localStackS3Client();
        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).objectLockEnabledForBucket(true).build());
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    private static S3Client localStackS3Client() {
        return S3Client.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .forcePathStyle(true)
                .build();
    }

    /** Points the real {@code ReceiptStore} at LocalStack. Production's {@code s3Client} bean
     * has no endpoint override. This bean has a different name, so there is no bean-override
     * clash, and {@code @Primary} makes it the one {@code ReceiptStore} receives by type. */
    @TestConfiguration
    static class LocalStackS3Configuration {
        @Bean
        @Primary
        S3Client localStackS3ClientBean() {
            return localStackS3Client();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private LedgerVerifier ledgerVerifier;

    @MockBean
    private JwtDecoder jwtDecoder;

    private UUID merchantAccountUuid;
    private UUID firstWatchId;
    private UUID replacementWatchId;
    private KafkaConsumer<String, String> outboundConsumer;
    private final List<ConsumerRecord<String, String>> outboundRecords = new ArrayList<>();

    @BeforeEach
    void stubCollaborators() {
        merchantAccountUuid = UUID.randomUUID();
        firstWatchId = UUID.randomUUID();
        replacementWatchId = UUID.randomUUID();

        Instant now = Instant.now();
        when(jwtDecoder.decode(anyString())).thenReturn(Jwt.withTokenValue("e2e-merchant-token")
                .header("alg", "none")
                .subject(merchantAccountUuid.toString())
                .claim("roles", List.of("MERCHANT"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build());

        WIREMOCK.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(okJson("{\"access_token\":\"e2e-crypto-token\",\"expires_in\":3600}")));
        // The first registration is the invoice's own watch. Any later one is the replacement
        // watch a SEEN -> WATCHING reorg registers (AC3).
        WIREMOCK.stubFor(post(urlEqualTo(WATCHES_PATH))
                .inScenario("watches").whenScenarioStateIs(Scenario.STARTED).willSetStateTo("replacement")
                .willReturn(okJson("{\"watchId\":\"" + firstWatchId + "\",\"status\":\"REGISTERED\"}")));
        WIREMOCK.stubFor(post(urlEqualTo(WATCHES_PATH))
                .inScenario("watches").whenScenarioStateIs("replacement")
                .willReturn(okJson("{\"watchId\":\"" + replacementWatchId + "\",\"status\":\"REGISTERED\"}")));
        WIREMOCK.stubFor(delete(urlPathMatching(WATCHES_PATH + "/.*"))
                .willReturn(aResponse().withStatus(204)));
        WIREMOCK.stubFor(post(urlEqualTo(ATTEST_PATH))
                .willReturn(okJson("{\"signature\":\"ZTJlLXNpZ25hdHVyZQ==\",\"kmsKeyId\":\"e2e-kms-key\","
                        + "\"signedAt\":\"2026-10-19T00:00:00Z\",\"outcome\":\"SIGNED\"}")));

        Properties consumerProperties = new Properties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-assertions-" + UUID.randomUUID());
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        outboundConsumer = new KafkaConsumer<>(consumerProperties);
        outboundConsumer.subscribe(List.of(
                "payments.invoice.created", "payments.payment.seen", "payments.payment.finalized", "payments.receipt.issued"));
        outboundRecords.clear();
    }

    /** Clears every table in FK order. S3 objects cannot be deleted, because they are WORM, so
     * every S3 assertion is scoped by receipt or payment UUID instead. */
    @AfterEach
    void cleanUp() throws SQLException {
        outboundConsumer.close();
        WIREMOCK.resetAll();
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM payments.ledger_entries");
            statement.execute("DELETE FROM payments.receipts");
            statement.execute("DELETE FROM payments.verification_records");
            statement.execute("DELETE FROM payments.processed_events");
            statement.execute("DELETE FROM payments.outbox");
            statement.execute("DELETE FROM payments.payments");
            statement.execute("DELETE FROM payments.invoices");
        }
    }

    /** AC1. The forward flow, from an authenticated invoice creation all the way to an
     * {@code ATTESTED} payment whose {@code receipt.issued} event arrives on Kafka. */
    @Test
    void shouldDriveInvoiceToAttestedReceiptEndToEnd() throws Exception {
        String txHash = "0x" + UUID.randomUUID();

        // 1. R1, R2, R6: invoice creation and the watch-ack.
        UUID invoiceUuid = createInvoice();
        // Phase 9 (Kimi F2, F8): the outbound watch request honours the Crypto Service
        // contract (design.md section 4c), and it carries the service-to-service token.
        List<LoggedRequest> watchCalls = WIREMOCK.findAll(postRequestedFor(urlEqualTo(WATCHES_PATH)));
        assertThat(watchCalls).hasSize(1);
        assertThat(watchCalls.get(0).getHeader("Authorization")).isEqualTo(CRYPTO_BEARER);
        JsonNode watchBody = OBJECT_MAPPER.readTree(watchCalls.get(0).getBodyAsString());
        assertThat(watchBody.get("invoiceUuid").asText()).isEqualTo(invoiceUuid.toString());
        assertThat(watchBody.get("chain").asText()).isEqualTo(CHAIN);
        assertThat(watchBody.get("address").asText()).isEqualTo(PAY_TO_ADDRESS);
        assertThat(watchBody.get("tokenContractAddress").asText()).isEqualTo(TOKEN_CONTRACT_ADDRESS);
        assertThat(watchBody.get("expectedAmount").isTextual()).as("L6: expectedAmount is a decimal string").isTrue();
        assertThat(watchBody.get("expectedAmount").asText()).isEqualTo("1000");
        assertThat(invoiceWatchId(invoiceUuid)).as("the acked watchId is persisted on the invoice").isEqualTo(firstWatchId);
        assertThat(outboxCount("invoice.created", invoiceUuid.toString())).isEqualTo(1);
        // Phase 9 (Kimi F3): the fourth outbox event also reaches the broker.
        awaitOutboundRecord("payments.invoice.created", invoiceUuid.toString());

        // 2. R7, R7a, R8: seen.
        sendAndWait("chain.tx.seen", firstWatchId.toString(), seenJson(txHash, invoiceUuid));
        awaitPaymentState(txHash, "SEEN");
        UUID paymentUuid = paymentUuid(txHash);
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                WIREMOCK.verify(1, deleteRequestedFor(urlEqualTo(WATCHES_PATH + "/" + firstWatchId))));
        awaitOutboundRecord("payments.payment.seen", paymentUuid.toString());

        // 3. R9: confirmed.
        sendAndWait("chain.tx.confirmed", firstWatchId.toString(), confirmedJson(txHash, invoiceUuid));
        awaitPaymentState(txHash, "CONFIRMING");

        // 4. R10, R17, R19, R21, R23, R24, R25, R30: finalized through to ATTESTED.
        sendAndWait("chain.tx.finalized", firstWatchId.toString(), finalizedJson(txHash, invoiceUuid));
        awaitPaymentState(txHash, "ATTESTED");
        assertThat(invoiceState(invoiceUuid)).isEqualTo("PAID");

        // R19: exactly one /attest call, carrying this payment's chain and txHash.
        List<LoggedRequest> attestCalls = WIREMOCK.findAll(postRequestedFor(urlEqualTo(ATTEST_PATH)));
        assertThat(attestCalls).hasSize(1);
        assertThat(attestCalls.get(0).getHeader("Authorization")).isEqualTo(CRYPTO_BEARER);
        JsonNode attestBody = OBJECT_MAPPER.readTree(attestCalls.get(0).getBodyAsString());
        assertThat(attestBody.get("chain").asText()).isEqualTo(CHAIN);
        assertThat(attestBody.get("txHash").asText()).isEqualTo(txHash);
        String attestedDigest = attestBody.get("receiptDigestSha256").asText();
        assertThat(attestedDigest).matches("[0-9a-f]{64}");

        ReceiptRow receipt = receiptFor(txHash);
        assertThat(receipt.digest()).as("the receipts row stores the digest that was signed").isEqualTo(attestedDigest);
        assertThat(receipt.s3Key()).isEqualTo(RECEIPT_PREFIX + receipt.receiptUuid());

        // R23 / L9 (Phase 3 F1, F7): both objects exist, are distinct, and are WORM.
        String jsonKey = receipt.s3Key() + ".json";
        String pdfKey = receipt.s3Key() + ".pdf";
        HeadObjectResponse jsonHead = s3.headObject(b -> b.bucket(BUCKET).key(jsonKey));
        HeadObjectResponse pdfHead = s3.headObject(b -> b.bucket(BUCKET).key(pdfKey));
        assertThat(jsonHead.contentType()).isEqualTo("application/json");
        assertThat(pdfHead.contentType()).isEqualTo("application/pdf");
        byte[] pdfBytes = s3.getObjectAsBytes(b -> b.bucket(BUCKET).key(pdfKey)).asByteArray();
        assertThat(new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        // Phase 9 (Kimi F6): the payment-uuid metadata that assertNoReceiptIssued relies on is
        // really written, so the reorg tests' S3 check can't silently go blind.
        for (HeadObjectResponse head : List.of(jsonHead, pdfHead)) {
            assertThat(head.metadata()).containsEntry("payment-uuid", paymentUuid.toString())
                    .containsEntry("receipt-uuid", receipt.receiptUuid().toString());
        }
        assertWormCompliance(jsonKey, jsonHead.versionId());
        assertWormCompliance(pdfKey, pdfHead.versionId());

        // R19 (Phase 3 F4): the digest that was signed is the canonical digest of the stored
        // receipt. Recomputed independently: the stored JSON minus the three signature fields,
        // through the shared CanonicalJson canonicalizer, then SHA-256.
        ResponseBytes<GetObjectResponse> storedJson = s3.getObjectAsBytes(b -> b.bucket(BUCKET).key(jsonKey));
        Map<String, Object> storedFields = OBJECT_MAPPER.readValue(storedJson.asUtf8String(),
                new TypeReference<LinkedHashMap<String, Object>>() { });
        assertThat(storedFields).containsEntry("signature", "ZTJlLXNpZ25hdHVyZQ==")
                .containsEntry("kmsKeyId", "e2e-kms-key");
        storedFields.remove("signature");
        storedFields.remove("kmsKeyId");
        storedFields.remove("signedAt");
        assertThat(sha256Hex(CanonicalJson.serialize(storedFields)))
                .as("the /attest digest must be the canonical digest of the stored receipt payload")
                .isEqualTo(attestedDigest);

        // R21 / L7 (Phase 3 F2): one ledger entry, chained to genesis on this empty ledger, and
        // the real verifier recomputes the whole chain intact.
        assertThat(ledgerPrevHashes()).as("exactly one ledger entry, chained to genesis")
                .containsExactly(GENESIS_PREV_HASH);
        assertThat(ledgerEntryCountForReceipt(receipt.receiptUuid())).isEqualTo(1);
        assertThat(ledgerVerifier.verify()).isInstanceOf(LedgerVerificationResult.Intact.class);

        // R10, R25, R30 (Phase 3 F3): payment.finalized and receipt.issued arrive on Kafka.
        // Phase 9 (Kimi F5): payload fields as well as keys.
        JsonNode finalizedBody = OBJECT_MAPPER.readTree(
                awaitOutboundRecord("payments.payment.finalized", paymentUuid.toString()).value());
        assertThat(finalizedBody.get("chain").asText()).isEqualTo(CHAIN);
        assertThat(finalizedBody.get("txHash").asText()).isEqualTo(txHash);
        assertThat(finalizedBody.get("amount").isTextual()).as("L6: amount is a decimal string").isTrue();
        ConsumerRecord<String, String> receiptIssued =
                awaitOutboundRecord("payments.receipt.issued", receipt.receiptUuid().toString());
        JsonNode receiptIssuedBody = OBJECT_MAPPER.readTree(receiptIssued.value());
        assertThat(receiptIssuedBody.get("receiptUuid").asText()).isEqualTo(receipt.receiptUuid().toString());
        assertThat(receiptIssuedBody.get("paymentUuid").asText()).isEqualTo(paymentUuid.toString());
        assertThat(receiptIssuedBody.get("invoiceUuid").asText()).isEqualTo(invoiceUuid.toString());
        assertThat(receiptIssuedBody.get("receiptUri").asText())
                .as("receiptUri points at the real stored JSON object")
                .isEqualTo("s3://" + BUCKET + "/" + jsonKey);
        assertThat(receiptIssuedBody.get("kmsKeyId").asText()).isEqualTo("e2e-kms-key");
        assertThat(receiptIssuedBody.get("amount").isTextual()).as("L6: amount is a decimal string").isTrue();
        assertThat(receiptIssuedBody.get("amount").asText()).isEqualTo("1000");
    }

    /** AC2 (R11, R18; L11). A reorg of a {@code CONFIRMING} payment walks it back to
     * {@code SEEN} and issues no receipt. */
    @Test
    void shouldWalkConfirmingBackToSeenOnReorgWithoutIssuingAReceipt() throws Exception {
        String txHash = "0x" + UUID.randomUUID();
        UUID invoiceUuid = createInvoice();

        sendAndWait("chain.tx.seen", firstWatchId.toString(), seenJson(txHash, invoiceUuid));
        awaitPaymentState(txHash, "SEEN");
        sendAndWait("chain.tx.confirmed", firstWatchId.toString(), confirmedJson(txHash, invoiceUuid));
        awaitPaymentState(txHash, "CONFIRMING");

        sendAndWait("chain.tx.reorged", firstWatchId.toString(), reorgedJson(txHash, invoiceUuid));
        awaitProcessed(CHAIN + ":" + txHash + ":reorged");

        assertThat(paymentState(txHash)).isEqualTo("SEEN");
        assertThat(verificationRecordExists(txHash, "CONFIRMING", "SEEN"))
                .as("R11: the reversal is recorded").isTrue();
        assertNoReceiptIssued(txHash);
    }

    /** AC3 (R12, R18; L11). A reorg of a {@code SEEN} payment walks it back to
     * {@code WATCHING}, registers a replacement watch, and issues no receipt. */
    @Test
    void shouldWalkSeenBackToWatchingOnReorgWithoutIssuingAReceipt() throws Exception {
        String txHash = "0x" + UUID.randomUUID();
        UUID invoiceUuid = createInvoice();

        sendAndWait("chain.tx.seen", firstWatchId.toString(), seenJson(txHash, invoiceUuid));
        awaitPaymentState(txHash, "SEEN");

        sendAndWait("chain.tx.reorged", firstWatchId.toString(), reorgedJson(txHash, invoiceUuid));
        awaitProcessed(CHAIN + ":" + txHash + ":reorged");

        assertThat(paymentState(txHash)).isEqualTo("WATCHING");
        assertThat(verificationRecordExists(txHash, "SEEN", "WATCHING"))
                .as("R12: the reversal is recorded").isTrue();
        // Phase 9 (Kimi F1): only the original watch was unregistered, on first SEEN (R7a); the
        // replacement watch stays live.
        WIREMOCK.verify(1, deleteRequestedFor(urlEqualTo(WATCHES_PATH + "/" + firstWatchId)));
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> {
            WIREMOCK.verify(2, postRequestedFor(urlEqualTo(WATCHES_PATH)).withHeader("Authorization", equalTo(CRYPTO_BEARER)));
            assertThat(invoiceWatchId(invoiceUuid))
                    .as("the replacement watch is persisted on the still-OPEN invoice")
                    .isEqualTo(replacementWatchId);
        });
        WIREMOCK.verify(0, deleteRequestedFor(urlEqualTo(WATCHES_PATH + "/" + replacementWatchId)));
        assertNoReceiptIssued(txHash);
    }

    // --- Flow helpers --------------------------------------------------------------------

    private UUID createInvoice() throws Exception {
        String body = """
                {"chain":"%s","tokenContractAddress":"%s","tokenDecimals":6,"amount":"1000",
                 "payToAddress":"%s","reference":"e2e"}
                """.formatted(CHAIN, TOKEN_CONTRACT_ADDRESS, PAY_TO_ADDRESS);
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/invoices")
                        .header("Authorization", "Bearer e2e-merchant-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("state").asText()).isEqualTo("OPEN");
        assertThat(response.get("payToAddress").asText()).isEqualTo(PAY_TO_ADDRESS);
        assertThat(response.get("merchantAccountUuid").asText()).isEqualTo(merchantAccountUuid.toString());
        return UUID.fromString(response.get("invoiceUuid").asText());
    }

    /** Waits on the real send future, so a delivery failure shows up here, not as a later
     * timeout (the T14 Phase 8 lesson). */
    private void sendAndWait(String topic, String key, String json) {
        try {
            kafkaTemplate.send(topic, key, json).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to send to " + topic, e);
        }
    }

    private void awaitPaymentState(String txHash, String expected) {
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> assertThat(paymentState(txHash)).isEqualTo(expected));
    }

    /** Before any "nothing happened" assertion, proves the triggering event really was consumed,
     * so the negative assertion can't pass vacuously (the T14 Phase 8 lesson). */
    private void awaitProcessed(String idempotencyKey) {
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> assertThat(processedEventExists(idempotencyKey)).isTrue());
    }

    /** Phase 9 (Kimi F7, = self-review F2): at least one record, not exactly one. The outbox
     * relay is at-least-once (L8): a send that times out after the broker accepted it is
     * legitimately resent. Any such duplicate must be identical, though. "Exactly one receipt"
     * (R17) stays pinned elsewhere: the single receipts row, the single /attest call and the
     * single ledger entry. */
    private ConsumerRecord<String, String> awaitOutboundRecord(String topic, String key) {
        await().atMost(ASYNC_TIMEOUT).pollInSameThread().untilAsserted(() -> {
            outboundConsumer.poll(Duration.ofMillis(200)).forEach(outboundRecords::add);
            assertThat(outboundRecords(topic, key)).as("records on %s keyed %s", topic, key).isNotEmpty();
        });
        List<ConsumerRecord<String, String>> records = outboundRecords(topic, key);
        assertThat(records.stream().map(ConsumerRecord::value).distinct())
                .as("any at-least-once redelivery on %s keyed %s must be an identical payload", topic, key)
                .hasSize(1);
        return records.get(0);
    }

    private List<ConsumerRecord<String, String>> outboundRecords(String topic, String key) {
        return outboundRecords.stream()
                .filter(r -> r.topic().equals(topic) && key.equals(r.key()))
                .toList();
    }

    private void assertWormCompliance(String key, String versionId) {
        assertThat(versionId).as("an Object Lock bucket is versioned").isNotBlank();
        // Phase 9 (Kimi F4): the retention read and the delete attempt target the same version.
        ObjectLockRetention retention = s3.getObjectRetention(b -> b.bucket(BUCKET).key(key).versionId(versionId))
                .retention();
        assertThat(retention.mode()).as("%s must be locked in COMPLIANCE mode", key)
                .isEqualTo(ObjectLockRetentionMode.COMPLIANCE);
        assertThat(retention.retainUntilDate()).isAfter(Instant.now());
        assertThatThrownBy(() -> s3.deleteObject(DeleteObjectRequest.builder()
                        .bucket(BUCKET).key(key).versionId(versionId).build()))
                .as("a COMPLIANCE-locked version of %s must not be deletable", key)
                .isInstanceOfSatisfying(S3Exception.class, e -> assertThat(e.statusCode()).isEqualTo(403));
    }

    /** R18 / L2 / L11: no attest call, no receipt or ledger row, and no S3 object for this
     * payment. WireMock is reset per test, so the attest count is scoped to this test. The S3
     * check goes by the {@code payment-uuid} object metadata, because WORM objects written by
     * earlier tests can't be cleaned up. */
    private void assertNoReceiptIssued(String txHash) throws SQLException {
        UUID paymentUuid = paymentUuid(txHash);
        WIREMOCK.verify(0, postRequestedFor(urlEqualTo(ATTEST_PATH)));
        assertThat(countForPayment("receipts", txHash)).as("no receipts row").isZero();
        assertThat(countLedgerEntries()).as("no ledger entry").isZero();
        List<S3Object> objects = s3.listObjectsV2(b -> b.bucket(BUCKET).prefix(RECEIPT_PREFIX)).contents();
        for (S3Object object : objects) {
            Map<String, String> metadata = s3.headObject(b -> b.bucket(BUCKET).key(object.key())).metadata();
            assertThat(metadata.get("payment-uuid")).as("no S3 receipt object for this payment")
                    .isNotEqualTo(paymentUuid.toString());
        }
    }

    // --- Event fixtures (amounts are always quoted strings; keys are chain:txhash:eventtype) ---

    private String seenJson(String txHash, UUID invoiceUuid) {
        return """
                {"idempotencyKey":"%s:%s:seen","watchId":"%s","invoiceUuid":"%s","chain":"%s",
                 "txHash":"%s","tokenContractAddress":"%s","confirmations":1,
                 "occurredAt":"2026-10-19T00:00:00Z"}
                """.formatted(CHAIN, txHash, firstWatchId, invoiceUuid, CHAIN, txHash, TOKEN_CONTRACT_ADDRESS);
    }

    private String confirmedJson(String txHash, UUID invoiceUuid) {
        return """
                {"idempotencyKey":"%s:%s:confirmed","watchId":"%s","invoiceUuid":"%s","chain":"%s",
                 "txHash":"%s","tokenContractAddress":"%s","confirmations":3,
                 "occurredAt":"2026-10-19T00:05:00Z"}
                """.formatted(CHAIN, txHash, firstWatchId, invoiceUuid, CHAIN, txHash, TOKEN_CONTRACT_ADDRESS);
    }

    private String finalizedJson(String txHash, UUID invoiceUuid) {
        return """
                {"idempotencyKey":"%s:%s:finalized","watchId":"%s","invoiceUuid":"%s","chain":"%s",
                 "txHash":"%s","fromAddress":"0xfrom","toAddress":"%s",
                 "tokenContractAddress":"%s","amount":"1000","confirmations":12,
                 "addressPoisoningFlag":false,"occurredAt":"2026-10-19T00:10:00Z"}
                """.formatted(CHAIN, txHash, firstWatchId, invoiceUuid, CHAIN, txHash, PAY_TO_ADDRESS,
                TOKEN_CONTRACT_ADDRESS);
    }

    private String reorgedJson(String txHash, UUID invoiceUuid) {
        return """
                {"idempotencyKey":"%s:%s:reorged","watchId":"%s","invoiceUuid":"%s","chain":"%s",
                 "txHash":"%s","tokenContractAddress":"%s","occurredAt":"2026-10-19T00:15:00Z"}
                """.formatted(CHAIN, txHash, firstWatchId, invoiceUuid, CHAIN, txHash, TOKEN_CONTRACT_ADDRESS);
    }

    // --- Raw admin-JDBC reads (L12: no entity is reachable from this cross-module test) ---

    private record ReceiptRow(UUID receiptUuid, String digest, String s3Key) {
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String queryString(String sql, Object parameter) throws SQLException {
        try (Connection admin = adminConnection(); PreparedStatement statement = admin.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private static int queryCount(String sql, Object... parameters) throws SQLException {
        try (Connection admin = adminConnection(); PreparedStatement statement = admin.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private static String paymentState(String txHash) throws SQLException {
        return queryString("SELECT state FROM payments.payments WHERE tx_hash = ?", txHash);
    }

    private static UUID paymentUuid(String txHash) throws SQLException {
        return UUID.fromString(queryString("SELECT payment_uuid FROM payments.payments WHERE tx_hash = ?", txHash));
    }

    private static String invoiceState(UUID invoiceUuid) throws SQLException {
        return queryString("SELECT state FROM payments.invoices WHERE invoice_uuid = ?", invoiceUuid);
    }

    private static UUID invoiceWatchId(UUID invoiceUuid) throws SQLException {
        String watchId = queryString("SELECT watch_id FROM payments.invoices WHERE invoice_uuid = ?", invoiceUuid);
        return watchId == null ? null : UUID.fromString(watchId);
    }

    private static int outboxCount(String eventType, String aggregateId) throws SQLException {
        return queryCount("SELECT COUNT(*) FROM payments.outbox WHERE event_type = ? AND aggregate_id = ?",
                eventType, aggregateId);
    }

    private static boolean processedEventExists(String idempotencyKey) throws SQLException {
        return queryCount("SELECT COUNT(*) FROM payments.processed_events WHERE event_key = ?", idempotencyKey) > 0;
    }

    private static boolean verificationRecordExists(String txHash, String fromState, String toState)
            throws SQLException {
        return queryCount("SELECT COUNT(*) FROM payments.verification_records v "
                        + "JOIN payments.payments p ON p.id = v.payment_id "
                        + "WHERE p.tx_hash = ? AND v.from_state = ? AND v.to_state = ?",
                txHash, fromState, toState) > 0;
    }

    private static ReceiptRow receiptFor(String txHash) throws SQLException {
        try (Connection admin = adminConnection(); PreparedStatement statement = admin.prepareStatement(
                "SELECT r.receipt_uuid, r.digest_sha256, r.s3_key FROM payments.receipts r "
                        + "JOIN payments.payments p ON p.id = r.payment_id WHERE p.tx_hash = ?")) {
            statement.setString(1, txHash);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as("a receipts row exists for %s", txHash).isTrue();
                ReceiptRow row = new ReceiptRow(UUID.fromString(resultSet.getString(1)),
                        resultSet.getString(2), resultSet.getString(3));
                assertThat(resultSet.next()).as("exactly one receipts row for %s", txHash).isFalse();
                return row;
            }
        }
    }

    private static List<String> ledgerPrevHashes() throws SQLException {
        List<String> prevHashes = new ArrayList<>();
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT prev_hash FROM payments.ledger_entries ORDER BY seq")) {
            while (resultSet.next()) {
                prevHashes.add(resultSet.getString(1));
            }
        }
        return prevHashes;
    }

    private static int ledgerEntryCountForReceipt(UUID receiptUuid) throws SQLException {
        return queryCount("SELECT COUNT(*) FROM payments.ledger_entries l "
                + "JOIN payments.receipts r ON r.id = l.receipt_id WHERE r.receipt_uuid = ?", receiptUuid);
    }

    private static int countLedgerEntries() throws SQLException {
        return queryCount("SELECT COUNT(*) FROM payments.ledger_entries");
    }

    private static int countForPayment(String table, String txHash) throws SQLException {
        return queryCount("SELECT COUNT(*) FROM payments." + table + " t "
                + "JOIN payments.payments p ON p.id = t.payment_id WHERE p.tx_hash = ?", txHash);
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
