package com.themistra.crypto.watch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.FakeChainAdapter;
import com.themistra.crypto.adapter.ProviderSet;
import com.themistra.crypto.adapter.model.FinalityStatus;
import com.themistra.crypto.adapter.model.TxResult;
import com.themistra.crypto.attest.Attestation;
import com.themistra.crypto.attest.AttestRequest;
import com.themistra.crypto.attest.KmsSigner;
import com.themistra.crypto.events.OutboxRelay;
import com.themistra.crypto.finality.FinalityPolicy;
import com.themistra.crypto.observation.Observation;
import com.themistra.crypto.observation.ObservationLog;
import com.themistra.crypto.observation.ObservationSnapshotStore;
import com.themistra.crypto.observation.FactType;
import com.themistra.crypto.provider.ProviderHealthTracker;
import com.themistra.crypto.quorum.HeldFactAlerter;
import com.themistra.crypto.quorum.QuorumDecisionService;
import com.themistra.crypto.reorg.ReorgDetector;
import com.themistra.crypto.screening.ScreeningClient;
import com.themistra.crypto.screening.ScreeningOutcome;
import com.themistra.crypto.screening.ScreeningResult;
import com.themistra.crypto.watch.dto.RegisterWatchRequest;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T26 — the one real, end-to-end proof that the pieces built across T02-T25 work together, through a
 * real Postgres and a real Kafka broker, not just in isolation. Four flows: (1) register → seen →
 * confirmed → finalized → attest returns a signature; (2) a genuine 2-of-3 disagreement on a
 * non-Boolean fact holds and emits nothing further; (3) a reorg after confirmed emits
 * {@code chain.tx.reorged}; (4) a sanctioned counterparty is blocked with no signature.
 *
 * <p><b>Real infrastructure, fake providers (`agents.md`).</b> {@code PostgreSQLContainer}/
 * {@code KafkaContainer} are real; every chain observation comes from a {@link FakeChainAdapter} —
 * real RPC is never called.</p>
 *
 * <p><b>Manual `Watcher` construction, real collaborators (Frozen Brief, architectural constraint).</b>
 * {@link ProviderSet}'s constructor is hard-typed to {@code List<EthereumAdapter>}/
 * {@code List<TronAdapter>}, not the {@code ChainAdapter} interface, so a {@link FakeChainAdapter}
 * cannot be wired through the real {@link WatcherRegistry}/{@link ProviderSet} path without a
 * production change (out of this task's scope). Each flow instead constructs its own {@link Watcher}
 * directly — mirroring {@code WatcherTest}'s own established technique — but every collaborator is a
 * real, Spring-autowired bean backed by the real containers, not a mock. {@link WatcherRegistry}
 * itself is {@code @MockBean}-doubled so its own real, {@code @Scheduled} {@code reconcile()} can never
 * start a second, real-RPC-backed {@code Watcher} racing this test's own (Frozen Brief Finding #15).</p>
 *
 * <p><b>Docker availability (Phase 0-5, disclosed, not silently assumed).</b> This class was written
 * and self-reviewed to compile cleanly and to be correct by construction against the real production
 * classes it exercises. Docker was not available in the development environment this class was
 * authored in, so its actual green/red run is deferred to a Docker-available environment — stated
 * plainly in the Phase 6 implementation notes, not silently claimed as passing.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class EndToEndIntegrationTest {

    private static final String INTERNAL_SCOPE = "SCOPE_internal.crypto:write";
    private static final long CORRELATION_WINDOW_MS = 60_000L;
    private static final long FINALITY_POLL_INTERVAL_MS = 3_600_000L;
    private static final String VALID_RECIPIENT = "0x5AEDA56215b167893e80B4fE645BA6d5Bab767DE";
    private static final String VALID_TOKEN_CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7";
    private static final String COUNTERPARTY_ADDRESS = "0xCounterpartyPaysFromThisAddress00000001";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @MockBean
    private WatcherRegistry watcherRegistry;
    @MockBean
    private ObservationSnapshotStore observationSnapshotStore;
    @MockBean
    private KmsSigner kmsSigner;
    @MockBean
    private ScreeningClient screeningClient;
    @MockBean
    private HeldFactAlerter heldFactAlerter;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ObservationLog observationLog;
    @Autowired
    private QuorumDecisionService quorumDecisionService;
    @Autowired
    private ProviderHealthTracker providerHealthTracker;
    @Autowired
    private ChainCursorRepository chainCursorRepository;
    @Autowired
    private TxLifecyclePublisher txLifecyclePublisher;
    @Autowired
    private ReorgDetector reorgDetector;
    @Autowired
    private OutboxRelay outboxRelay;
    @Autowired
    private List<FinalityPolicy> finalityPolicies;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private Clock clock;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private KafkaConsumer<String, String> consumer;
    /** T26 Phase 8/9 Finding #9: tracked so {@link #tearDown()} can stop it even if an earlier
     * assertion in the same test throws - unlike {@code WatcherTest}'s own fully-mocked, per-method-
     * isolated instances, this {@code Watcher} runs its scheduler against the real, class-shared
     * Postgres/Kafka containers, so a leaked one could interfere with a later test method. */
    private Watcher activeWatcher;
    /** T26 self-review: {@code chain.tx.seen} and {@code chain.tx.confirmed} can legitimately arrive
     * in the same {@code poll()} batch (both facts can agree from the same delivery round). A naive
     * "poll and return on first match" helper would silently lose the second record - once a batch is
     * fetched, Kafka's consumer position has moved past every record in it regardless of whether the
     * caller inspected each one, so it is never redelivered. Every polled record is buffered here
     * instead, and searched (not just newly-polled ones) on every lookup. */
    private final List<ConsumerRecord<String, String>> receivedRecords = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("chain.tx.seen", "chain.tx.confirmed", "chain.tx.finalized",
                "chain.tx.reorged", "chain.provider.degraded"));
        // Force initial partition assignment before any test produces a record - a bare subscribe()
        // is lazy and would otherwise race the first relay() call (Frozen Brief Finding #9).
        consumer.poll(Duration.ofMillis(500));
    }

    /** Unlike {@link Watcher#stop()} (each test's own responsibility, called explicitly at the end of
     * every flow) this consumer wraps a real network connection to the Testcontainers broker, not an
     * in-process virtual-thread scheduler — closed unconditionally here so a failing assertion earlier
     * in a test still releases it. */
    @AfterEach
    void tearDown() {
        if (activeWatcher != null) {
            activeWatcher.stop();
        }
        consumer.close();
    }

    private Watcher newRealWatcher(Watch watch, List<ProviderSet.NamedAdapter> adapters) {
        return new Watcher(watch, adapters, observationLog, quorumDecisionService, providerHealthTracker,
                chainCursorRepository, CORRELATION_WINDOW_MS, meterRegistry, clock, objectMapper,
                txLifecyclePublisher, finalityPolicies, FINALITY_POLL_INTERVAL_MS, reorgDetector);
    }

    private UUID registerWatch(String expectedAmount) throws Exception {
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_RECIPIENT, VALID_TOKEN_CONTRACT, expectedAmount, Instant.now(clock).plusSeconds(86_400));
        String responseJson = mockMvc.perform(post("/internal/v1/watches")
                        .with(jwt().authorities(new SimpleGrantedAuthority(INTERNAL_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(responseJson).get("watchId").asText());
    }

    /** T26 Phase 8/9 Finding #4: {@code txHash} must be threaded through explicitly - the original
     * version hardcoded {@code "0xtxhash"} regardless of the map key each flow scripted it under,
     * so every flow's {@link TxResult} secretly shared one identical transaction hash, colliding on
     * the outbox's own idempotency-key uniqueness and on this test's own Kafka assertions. */
    private static TxResult tx(String txHash, boolean exists, long blockNumber, BigDecimal amount,
                                int confirmations) {
        return new TxResult(exists, txHash, COUNTERPARTY_ADDRESS, VALID_RECIPIENT,
                VALID_TOKEN_CONTRACT, amount, confirmations, blockNumber);
    }

    private static ProviderSet.NamedAdapter named(String name, FakeChainAdapter adapter) {
        return new ProviderSet.NamedAdapter(name, adapter);
    }

    /** {@code com.themistra.crypto.attest.SignatureResult} is a package-private record - invisible
     * from this test's own {@code watch} package (required for direct {@link Watcher} construction,
     * see the class Javadoc). Reflection is the narrowest fix: it avoids both a production-code
     * visibility change (out of this task's scope) and a new cross-package test-fixture file (not
     * part of the frozen brief's authorized Files to Create). {@link org.mockito.Mockito#doReturn}
     * accepts a raw {@code Object}, so the caller never needs to name the actual return type. */
    private static Object newSignatureResult(String signatureBase64, String kmsKeyId, Instant signedAt)
            throws ReflectiveOperationException {
        Class<?> signatureResultClass = Class.forName("com.themistra.crypto.attest.SignatureResult");
        var constructor = signatureResultClass.getDeclaredConstructor(String.class, String.class, Instant.class);
        constructor.setAccessible(true);
        return constructor.newInstance(signatureBase64, kmsKeyId, signedAt);
    }

    @SuppressWarnings("unchecked")
    private List<Observation> findObservations(String chain, String txHash, FactType factType) {
        return entityManager.createQuery(
                        "select o from Observation o where o.chain = :chain and o.txHash = :txHash "
                                + "and o.factType = :factType")
                .setParameter("chain", chain)
                .setParameter("txHash", txHash)
                .setParameter("factType", factType)
                .getResultList();
    }

    /** T26 Phase 11 Finding #8: the HTTP response shape alone doesn't prove a durable audit row
     * exists - R20/R21 require every attestation outcome to be persisted. */
    @SuppressWarnings("unchecked")
    private List<Attestation> findAttestations(String chain, String txHash) {
        return entityManager.createQuery(
                        "select a from Attestation a where a.chain = :chain and a.txHash = :txHash")
                .setParameter("chain", chain)
                .setParameter("txHash", txHash)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<ScreeningResult> findScreeningResults(String chain, String address) {
        return entityManager.createQuery(
                        "select s from ScreeningResult s where s.chain = :chain and s.address = :address")
                .setParameter("chain", chain)
                .setParameter("address", address)
                .getResultList();
    }

    /** T26 Phase 11 Findings #1/#4: filters on the record's own parsed {@code txHash} payload field,
     * not just the topic. Two distinct problems this closes together: (1) the {@code KafkaContainer}
     * and its topics are class-scoped, and every test's consumer group is brand-new with
     * {@code auto.offset.reset=earliest}, so without this filter a later flow's consumer would see
     * every earlier flow's messages too, on the same topic, and could satisfy (or wrongly fail) an
     * assertion using another flow's leftover record entirely; (2) a topic-only match never actually
     * validates the event's own content (Finding #4) - matching on {@code txHash} is the cheapest
     * meaningful content check every one of this task's own emitted-event payload shapes (T23's
     * {@code SeenPayload}/{@code ConfirmedPayload}/{@code FinalizedPayload}/{@code ReorgedPayload})
     * carries in common. A matched record is removed from the buffer once returned (Phase 8/9 Finding
     * #10), so a later {@link #noRecordAppearsOnTopic} check can never report a stale "found". */
    private ConsumerRecord<String, String> awaitRecordOnTopic(String topic, String expectedTxHash,
                                                                Duration timeout) {
        Optional<ConsumerRecord<String, String>> alreadyBuffered = receivedRecords.stream()
                .filter(record -> record.topic().equals(topic) && recordHasTxHash(record, expectedTxHash))
                .findFirst();
        if (alreadyBuffered.isPresent()) {
            receivedRecords.remove(alreadyBuffered.get());
            return alreadyBuffered.get();
        }
        Instant deadline = Instant.now(clock).plus(timeout);
        while (Instant.now(clock).isBefore(deadline)) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            records.forEach(receivedRecords::add);
            for (ConsumerRecord<String, String> record : records) {
                if (record.topic().equals(topic) && recordHasTxHash(record, expectedTxHash)) {
                    receivedRecords.remove(record);
                    return record;
                }
            }
        }
        return null;
    }

    private boolean recordHasTxHash(ConsumerRecord<String, String> record, String expectedTxHash) {
        try {
            return objectMapper.readTree(record.value()).get("txHash").asText().equals(expectedTxHash);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean noRecordAppearsOnTopic(String topic, String txHash, Duration window) {
        return awaitRecordOnTopic(topic, txHash, window) == null;
    }

    /** T26 Phase 11 Finding #6: monotonic wall-clock ordering across a flow's own lifecycle events,
     * using each record's own Kafka-assigned produce timestamp (offsets aren't comparable across
     * different topics' independent partitions, but timestamps are). */
    private void assertStrictlyOrdered(ConsumerRecord<String, String> earlier,
                                        ConsumerRecord<String, String> later) {
        assertThat(earlier.timestamp())
                .as("%s (topic %s) must be produced before %s (topic %s)", earlier.value(), earlier.topic(),
                        later.value(), later.topic())
                .isLessThanOrEqualTo(later.timestamp());
    }

    @Test
    void endToEndFlowRegistersObservesAndAttestsWithASignature() throws Exception {
        UUID watchId = registerWatch("1000000");
        Watch watch = loadWatch(watchId);
        String txHash = "0xtxhash1";

        FakeChainAdapter providerA = new FakeChainAdapter(Chain.ETHEREUM, "provider-a");
        FakeChainAdapter providerB = new FakeChainAdapter(Chain.ETHEREUM, "provider-b");
        FakeChainAdapter providerC = new FakeChainAdapter(Chain.ETHEREUM, "provider-c");
        List<ProviderSet.NamedAdapter> adapters = List.of(
                named("provider-a", providerA), named("provider-b", providerB), named("provider-c", providerC));
        activeWatcher = newRealWatcher(watch, adapters);
        activeWatcher.start();

        TxResult agreed = tx(txHash, true, 100L, BigDecimal.valueOf(1_000_000L), 3);
        providerA.simulateReorg(txHash, agreed);
        providerB.simulateReorg(txHash, agreed);
        providerC.simulateReorg(txHash, agreed);
        outboxRelay.relay();

        ConsumerRecord<String, String> seenRecord = awaitRecordOnTopic("chain.tx.seen", txHash, Duration.ofSeconds(10));
        ConsumerRecord<String, String> confirmedRecord =
                awaitRecordOnTopic("chain.tx.confirmed", txHash, Duration.ofSeconds(10));
        assertThat(seenRecord).isNotNull();
        assertThat(confirmedRecord).isNotNull();
        assertStrictlyOrdered(seenRecord, confirmedRecord);

        assertThat(findObservations("ETHEREUM", txHash, FactType.EXISTENCE)).hasSize(3);
        assertThat(findObservations("ETHEREUM", txHash, FactType.AMOUNT)).hasSize(3);
        assertThat(quorumDecisionService.isAgreed("ETHEREUM", txHash, FactType.EXISTENCE)).isTrue();
        // Phase 11 Finding #10: TOKEN is one of AttestationService's own REQUIRED_FACTS alongside
        // EXISTENCE/AMOUNT/FINALITY - asserted explicitly so a missing/wrong TOKEN decision fails
        // here, not only indirectly via a later attest-gate failure.
        assertThat(quorumDecisionService.isAgreed("ETHEREUM", txHash, FactType.TOKEN)).isTrue();

        FinalityStatus finalStatus = new FinalityStatus(100L, 200L, 150L);
        providerA.scriptFinalityStatus(txHash, finalStatus);
        providerB.scriptFinalityStatus(txHash, finalStatus);
        providerC.scriptFinalityStatus(txHash, finalStatus);
        activeWatcher.pollFinality();
        outboxRelay.relay();

        ConsumerRecord<String, String> finalizedRecord =
                awaitRecordOnTopic("chain.tx.finalized", txHash, Duration.ofSeconds(10));
        assertThat(finalizedRecord).isNotNull();
        assertStrictlyOrdered(confirmedRecord, finalizedRecord);
        assertThat(quorumDecisionService.isAgreed("ETHEREUM", txHash, FactType.FINALITY)).isTrue();

        doReturn(newSignatureResult("c2ln", "test-key", Instant.now(clock))).when(kmsSigner).sign(any());

        AttestRequest attestRequest = new AttestRequest("d".repeat(64), "ETHEREUM", txHash);
        mockMvc.perform(post("/internal/v1/attest")
                        .with(jwt().authorities(new SimpleGrantedAuthority(INTERNAL_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attestRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SIGNED"))
                .andExpect(jsonPath("$.signature").value("c2ln"));

        // Phase 11 Finding #7: a mocked signer returning a fixed value without ever being invoked
        // could otherwise still produce this exact response if the service short-circuited signing.
        verify(kmsSigner, times(1)).sign(any());
        // Phase 11 Finding #9: a bug that alerted on a healthy flow would otherwise go unnoticed.
        verify(heldFactAlerter, never()).alert(anyString(), anyString(), any(), any());
        // Phase 11 Finding #8: the HTTP response alone doesn't prove a durable audit row exists (R20).
        assertThat(findAttestations("ETHEREUM", txHash))
                .anyMatch(attestation -> "SIGNED".equals(attestation.outcome().name()));
    }

    @Test
    void disagreementOnANonBooleanFactHoldsAndEmitsNothingFurther() throws Exception {
        UUID watchId = registerWatch("1000000");
        Watch watch = loadWatch(watchId);
        String txHash = "0xtxhash2";

        FakeChainAdapter providerA = new FakeChainAdapter(Chain.ETHEREUM, "provider-a");
        FakeChainAdapter providerB = new FakeChainAdapter(Chain.ETHEREUM, "provider-b");
        FakeChainAdapter providerC = new FakeChainAdapter(Chain.ETHEREUM, "provider-c");
        List<ProviderSet.NamedAdapter> adapters = List.of(
                named("provider-a", providerA), named("provider-b", providerB), named("provider-c", providerC));
        activeWatcher = newRealWatcher(watch, adapters);
        activeWatcher.start();

        // EXISTENCE agrees for all three (a genuine 3-way split is impossible for a Boolean fact,
        // Frozen Brief Finding #3), but AMOUNT and CONFIRMATIONS are each three genuinely distinct
        // values - no 2-of-3 majority for either (Phase 8/9 Finding #5: confirmations must also
        // disagree, or CONFIRMATIONS alone reaches AGREED and chain.tx.confirmed fires anyway).
        providerA.simulateReorg(txHash, tx(txHash, true, 100L, BigDecimal.valueOf(1L), 1));
        providerB.simulateReorg(txHash, tx(txHash, true, 100L, BigDecimal.valueOf(2L), 2));
        providerC.simulateReorg(txHash, tx(txHash, true, 100L, BigDecimal.valueOf(3L), 3));
        outboxRelay.relay();

        assertThat(awaitRecordOnTopic("chain.tx.seen", txHash, Duration.ofSeconds(10))).isNotNull();
        assertThat(quorumDecisionService.isAgreed("ETHEREUM", txHash, FactType.AMOUNT)).isFalse();
        assertThat(quorumDecisionService.isAgreed("ETHEREUM", txHash, FactType.CONFIRMATIONS)).isFalse();
        verify(heldFactAlerter).alert(eq("ETHEREUM"), eq(txHash), eq(FactType.AMOUNT), any());
        verify(heldFactAlerter).alert(eq("ETHEREUM"), eq(txHash), eq(FactType.CONFIRMATIONS), any());

        assertThat(noRecordAppearsOnTopic("chain.tx.confirmed", txHash, Duration.ofSeconds(5))).isTrue();
        assertThat(noRecordAppearsOnTopic("chain.tx.finalized", txHash, Duration.ofSeconds(2))).isTrue();
        verify(kmsSigner, never()).sign(any());
    }

    @Test
    void reorgAfterConfirmedEmitsReorgedAndInvalidatesTheCursor() throws Exception {
        UUID watchId = registerWatch("1000000");
        Watch watch = loadWatch(watchId);
        String txHash = "0xtxhash3";

        FakeChainAdapter providerA = new FakeChainAdapter(Chain.ETHEREUM, "provider-a");
        FakeChainAdapter providerB = new FakeChainAdapter(Chain.ETHEREUM, "provider-b");
        FakeChainAdapter providerC = new FakeChainAdapter(Chain.ETHEREUM, "provider-c");
        List<ProviderSet.NamedAdapter> adapters = List.of(
                named("provider-a", providerA), named("provider-b", providerB), named("provider-c", providerC));
        activeWatcher = newRealWatcher(watch, adapters);
        activeWatcher.start();

        TxResult agreed = tx(txHash, true, 100L, BigDecimal.valueOf(1_000_000L), 3);
        providerA.simulateReorg(txHash, agreed);
        providerB.simulateReorg(txHash, agreed);
        providerC.simulateReorg(txHash, agreed);
        outboxRelay.relay();
        ConsumerRecord<String, String> seenRecord = awaitRecordOnTopic("chain.tx.seen", txHash, Duration.ofSeconds(10));
        ConsumerRecord<String, String> confirmedRecord =
                awaitRecordOnTopic("chain.tx.confirmed", txHash, Duration.ofSeconds(10));
        assertThat(seenRecord).isNotNull();
        assertThat(confirmedRecord).isNotNull();
        assertStrictlyOrdered(seenRecord, confirmedRecord);

        TxResult reorgedAway = tx(txHash, false, 0L, null, 0);
        providerA.scriptTx(txHash, reorgedAway);
        providerB.scriptTx(txHash, reorgedAway);
        providerC.scriptTx(txHash, reorgedAway);
        activeWatcher.pollFinality();
        outboxRelay.relay();

        ConsumerRecord<String, String> reorgedRecord =
                awaitRecordOnTopic("chain.tx.reorged", txHash, Duration.ofSeconds(10));
        assertThat(reorgedRecord).isNotNull();
        assertStrictlyOrdered(confirmedRecord, reorgedRecord);

        Optional<ChainCursor> cursor = chainCursorRepository.findByWatchId(watchId);
        assertThat(cursor).isPresent();
        assertThat(cursor.get().txHash()).isNull();

        assertThat(noRecordAppearsOnTopic("chain.tx.finalized", txHash, Duration.ofSeconds(5))).isTrue();
        verify(heldFactAlerter, never()).alert(anyString(), anyString(), any(), any());
    }

    @Test
    void sanctionedCounterpartyIsBlockedWithNoSignature() throws Exception {
        UUID watchId = registerWatch("1000000");
        Watch watch = loadWatch(watchId);
        String txHash = "0xtxhash4";

        FakeChainAdapter providerA = new FakeChainAdapter(Chain.ETHEREUM, "provider-a");
        FakeChainAdapter providerB = new FakeChainAdapter(Chain.ETHEREUM, "provider-b");
        FakeChainAdapter providerC = new FakeChainAdapter(Chain.ETHEREUM, "provider-c");
        List<ProviderSet.NamedAdapter> adapters = List.of(
                named("provider-a", providerA), named("provider-b", providerB), named("provider-c", providerC));
        activeWatcher = newRealWatcher(watch, adapters);
        activeWatcher.start();

        TxResult agreed = tx(txHash, true, 100L, BigDecimal.valueOf(1_000_000L), 3);
        providerA.simulateReorg(txHash, agreed);
        providerB.simulateReorg(txHash, agreed);
        providerC.simulateReorg(txHash, agreed);
        outboxRelay.relay();
        ConsumerRecord<String, String> seenRecord = awaitRecordOnTopic("chain.tx.seen", txHash, Duration.ofSeconds(10));
        ConsumerRecord<String, String> confirmedRecord =
                awaitRecordOnTopic("chain.tx.confirmed", txHash, Duration.ofSeconds(10));
        assertThat(seenRecord).isNotNull();
        assertThat(confirmedRecord).isNotNull();
        assertStrictlyOrdered(seenRecord, confirmedRecord);

        FinalityStatus finalStatus = new FinalityStatus(100L, 200L, 150L);
        providerA.scriptFinalityStatus(txHash, finalStatus);
        providerB.scriptFinalityStatus(txHash, finalStatus);
        providerC.scriptFinalityStatus(txHash, finalStatus);
        activeWatcher.pollFinality();
        outboxRelay.relay();
        ConsumerRecord<String, String> finalizedRecord =
                awaitRecordOnTopic("chain.tx.finalized", txHash, Duration.ofSeconds(10));
        assertThat(finalizedRecord).isNotNull();
        assertStrictlyOrdered(confirmedRecord, finalizedRecord);

        // Mirrors the real ScreeningClient contract (screening/ScreeningClient.java): every call
        // persists exactly one ScreeningResult row before returning, regardless of outcome. Wrapped in
        // a TransactionTemplate (Phase 8/9 Finding #8) - AttestationService itself is deliberately not
        // @Transactional, so entityManager.persist(...) called from within this mocked answer, reached
        // via the real HTTP call below, would otherwise have no active transaction to join and throw
        // TransactionRequiredException.
        doAnswer(invocation -> {
            String chain = invocation.getArgument(0);
            String address = invocation.getArgument(1);
            String answerTxHash = invocation.getArgument(2);
            transactionTemplate.executeWithoutResult(status -> {
                ScreeningResult result = ScreeningResult.create(chain, address, answerTxHash,
                        ScreeningOutcome.BLOCKED, "test-sanctions-list",
                        "{\"match\":\"OFAC (test fixture)\"}", Instant.now(clock));
                entityManager.persist(result);
            });
            return ScreeningOutcome.BLOCKED;
        }).when(screeningClient).screen(anyString(), anyString(), anyString());

        AttestRequest attestRequest = new AttestRequest("d".repeat(64), "ETHEREUM", txHash);
        mockMvc.perform(post("/internal/v1/attest")
                        .with(jwt().authorities(new SimpleGrantedAuthority(INTERNAL_SCOPE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attestRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("BLOCKED"));

        verify(kmsSigner, never()).sign(any());
        verify(heldFactAlerter, never()).alert(anyString(), anyString(), any(), any());
        assertThat(findScreeningResults("ETHEREUM", COUNTERPARTY_ADDRESS))
                .anyMatch(result -> result.outcome() == ScreeningOutcome.BLOCKED);
        assertThat(findAttestations("ETHEREUM", txHash))
                .anyMatch(attestation -> "BLOCKED".equals(attestation.outcome().name()));
    }

    /** T26 Phase 8/9 Finding #3: {@link Watch}'s real JPA {@code @Id} is an auto-generated surrogate
     * {@code Long id} - {@code watchId} (the UUID returned to callers, and the one this test actually
     * has) is a separate, non-{@code @Id} column. {@code entityManager.find(Watch.class, watchId)}
     * would look up by the wrong key entirely and silently return {@code null}. Queried by the real
     * {@code watchId} column via JPQL instead. */
    private Watch loadWatch(UUID watchId) {
        return entityManager.createQuery("select w from Watch w where w.watchId = :watchId", Watch.class)
                .setParameter("watchId", watchId)
                .getSingleResult();
    }
}
